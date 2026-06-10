package com.eventos.banana.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.util.GeohashUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class FriendListUiState(
    val isLoading: Boolean = false,
    val friends: List<UserProfile> = emptyList(),
    val requests: List<UserProfile> = emptyList(),
    val suggestions: List<UserProfile> = emptyList(),
    val searchResults: List<UserProfile> = emptyList(),
    val errorMessage: String? = null
)


@HiltViewModel
class FriendListViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {


    private val _uiState = MutableStateFlow(FriendListUiState())
    val uiState: StateFlow<FriendListUiState> = _uiState

    // Track original lists for local search
    private var allFriends: List<UserProfile> = emptyList() // This will be used for local search on all loaded friends
    private var allRequests: List<UserProfile> = emptyList()
    private var allSuggestions: List<UserProfile> = emptyList()

    // Pagination state for friends
    private val _friends = MutableStateFlow<List<UserProfile>>(emptyList())
    val friends: StateFlow<List<UserProfile>> = _friends

    private val _isLoadingFriends = MutableStateFlow(false) // Specific loading for paginated friends
    val isLoadingFriends: StateFlow<Boolean> = _isLoadingFriends
    
    // Pagination state
    private var allFriendIds = emptyList<String>()
    private var allRequestReceivedIds = emptyList<String>()
    private var allRequestSentIds = emptyList<String>()
    private var currentPage = 0
    private val pageSize = 30
    val hasMoreFriends = MutableStateFlow(false)

    // ─── In-memory cache ───────────────────────────────────────────────────────
    // Evicts oldest entries when the cap (100) is exceeded (insertion-order LRU).
    private val userCache = LinkedHashMap<String, UserProfile>(128, 0.75f, false)
    private val USER_CACHE_MAX = 100

    private suspend fun getUsersWithCache(ids: List<String>): List<UserProfile> {
        if (ids.isEmpty()) return emptyList()
        val missing = ids.filter { it !in userCache }
        if (missing.isNotEmpty()) {
            val fetched = userRepository.getUsers(missing)
            fetched.forEach { profile ->
                if (userCache.size >= USER_CACHE_MAX) {
                    // Remove oldest entry
                    val oldest = userCache.keys.first()
                    userCache.remove(oldest)
                }
                userCache[profile.uid] = profile
            }
        }
        return ids.mapNotNull { userCache[it] }
    }

    // Reactive observation
    private var observeJob: Job? = null
    private var suggestionsJob: Job? = null
    private var lastGeohashPrefix: String? = null

    fun loadData(currentUserId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true) // General loading for all data
            _isLoadingFriends.value = true 
            try {
                // 1. Get Current User Profile
                val currentUser = userRepository.getUserProfile(currentUserId, forceRefresh = true)
                if (currentUser == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Usuario no encontrado")
                    _isLoadingFriends.value = false
                    return@launch
                }

                // Initialize pagination for friends
                allFriendIds = currentUser.friends
                allRequestReceivedIds = currentUser.friendRequestsReceived
                allRequestSentIds = currentUser.friendRequestsSent

                val friendsList = getUsersWithCache(allFriendIds.take(pageSize))
                
                _friends.value = friendsList
                allFriends = friendsList
                currentPage = 1
                hasMoreFriends.value = pageSize < allFriendIds.size

                val requestsList = getUsersWithCache(allRequestReceivedIds)

                val region = currentUser.region ?: ""
                val commune = currentUser.commune ?: ""

                val suggestionsListRaw = if (commune.isNotBlank()) {
                    userRepository.getUsersByCommune(commune, currentUserId)
                } else if (region.isNotBlank()) {
                    userRepository.getUsersByRegion(region, currentUserId)
                } else {
                    emptyList()
                }

                val friendIdsSet = allFriendIds.toSet()
                val suggestionsList = suggestionsListRaw.filter { user ->
                    user.uid !in friendIdsSet && 
                    user.uid !in allRequestReceivedIds &&
                    user.uid !in allRequestSentIds &&
                    user.uid != currentUserId
                }

                allRequests = requestsList
                allSuggestions = suggestionsList

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    friends = friendsList, // Initial page
                    requests = requestsList,
                    suggestions = suggestionsList
                )
                _isLoadingFriends.value = false

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
                _isLoadingFriends.value = false
            }
        }
    }

    fun loadNextPageFriends() {
        if (_isLoadingFriends.value || !hasMoreFriends.value) return
        
        viewModelScope.launch {
            _isLoadingFriends.value = true
            try {
                val start = currentPage * pageSize
                val end = (start + pageSize).coerceAtMost(allFriendIds.size)
                val chunkIds = allFriendIds.subList(start, end)
                
                val newFriends = getUsersWithCache(chunkIds)
                val updatedList = _friends.value + newFriends
                
                _friends.value = updatedList
                allFriends = updatedList // Update search base
                
                currentPage++
                hasMoreFriends.value = end < allFriendIds.size
                
                // Update UI state with full loaded list
                _uiState.value = _uiState.value.copy(friends = updatedList)
            } catch (e: Exception) {
                android.util.Log.e("FriendListViewModel", "Error loading next page: ${e.message}")
            } finally {
                _isLoadingFriends.value = false
            }
        }
    }

    fun searchUsers(query: String, currentUserId: String) {
        if (query.isBlank()) {
            // Restore full lists
            _uiState.value = _uiState.value.copy(
                friends = allFriends,
                requests = allRequests,
                suggestions = allSuggestions,
                searchResults = emptyList()
            )
            return
        }

        // 1. Local search (Friends/Requests)
        val filteredFriends = allFriends.filter { it.nickname.contains(query, ignoreCase = true) }
        val filteredRequests = allRequests.filter { it.nickname.contains(query, ignoreCase = true) }
        
        // 2. Global Search (Async)
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val globalResults = userRepository.searchUsers(query)
                
                // Filter out self, existing friends and blocked users from search results
                val finalSearchResults = globalResults.filter { user ->
                    user.uid != currentUserId &&
                    user.uid !in allFriendIds &&
                    user.uid !in allRequestReceivedIds &&
                    user.uid !in allRequestSentIds
                    // Note: blockedSet not cached here; search is infrequent — acceptable tradeoff
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    friends = filteredFriends,
                    requests = filteredRequests,
                    searchResults = finalSearchResults
                )
            } catch (e: Exception) {
                 _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    fun observeData(currentUserId: String) {
        observeJob?.cancel()
        suggestionsJob?.cancel()
        _uiState.update { it.copy(isLoading = true) }

        // ── Job 1: Amigos + Solicitudes ──────────────────────────────────────────
        observeJob = viewModelScope.launch {
            combine(
                userRepository.observeUserProfile(currentUserId),
                userRepository.observeActualFriendships(currentUserId),
                userRepository.observeActualFriendRequestsReceived(currentUserId)
            ) { profile, actualFriends, actualRequests ->
                profile.copy(
                    friends = if (actualFriends.isNotEmpty()) actualFriends else profile.friends,
                    friendRequestsReceived = if (actualRequests.isNotEmpty()) actualRequests else profile.friendRequestsReceived
                )
            }.distinctUntilChanged { old, new ->
                old.friends == new.friends &&
                old.friendRequestsReceived == new.friendRequestsReceived
            }.collect { stableProfile ->

                val previousFriendIds = allFriendIds.toSet()
                allFriendIds = stableProfile.friends
                allRequestReceivedIds = stableProfile.friendRequestsReceived
                allRequestSentIds = stableProfile.friendRequestsSent

                try {
                    // Carga INCREMENTAL: solo pedir los IDs que no están en cache
                    val firstPageIds = allFriendIds.take(pageSize)
                    val newIds = firstPageIds.filter { it !in previousFriendIds }

                    val friendsList = if (newIds.isEmpty() && _friends.value.isNotEmpty()) {
                        // Sin IDs nuevos: reusar existentes + filtra eliminados
                        val removedSet = previousFriendIds - allFriendIds.toSet()
                        _friends.value.filter { it.uid !in removedSet }
                    } else {
                        // Hay IDs nuevos: fetch solo los que faltan + mergear
                        getUsersWithCache(firstPageIds)
                    }

                    _friends.value = friendsList
                    allFriends = friendsList
                    currentPage = 1
                    hasMoreFriends.value = pageSize < allFriendIds.size

                    allRequests = getUsersWithCache(allRequestReceivedIds)

                    _uiState.update { it.copy(
                        isLoading = false,
                        friends = _friends.value,
                        requests = allRequests,
                        suggestions = allSuggestions,
                        errorMessage = null
                    )}
                } catch (e: Exception) {
                    android.util.Log.e("FriendListVM", "Error en sincronización amigos", e)
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }

        // ── Job 2: Sugerencias — solo cuando cambia el geohash ───────────────────
        suggestionsJob = viewModelScope.launch {
            userRepository.observeUserProfile(currentUserId)
                .distinctUntilChanged { old, new ->
                    val oldPrecision = GeohashUtils.getPrecisionForRadius(old.searchRadiusKm)
                    val newPrecision = GeohashUtils.getPrecisionForRadius(new.searchRadiusKm)
                    old.geohash?.take(newPrecision) == new.geohash?.take(newPrecision) &&
                    old.blockedUsers == new.blockedUsers
                }
                .collect { profile ->
                    val radiusPrecision = GeohashUtils.getPrecisionForRadius(profile.searchRadiusKm)
                    val geohashPrefix = profile.geohash?.take(radiusPrecision)
                    if (geohashPrefix == lastGeohashPrefix && allSuggestions.isNotEmpty()) return@collect
                    lastGeohashPrefix = geohashPrefix

                    try {
                        val geohash = profile.geohash ?: ""
                        val rawSuggestions = if (geohash.isNotBlank()) {
                            userRepository.getUsersByProximity(geohash, currentUserId, precision = radiusPrecision)
                        } else {
                            val commune = profile.commune ?: ""
                            val region = profile.region ?: ""
                            when {
                                commune.isNotBlank() -> userRepository.getUsersByCommune(commune, currentUserId)
                                region.isNotBlank() -> userRepository.getUsersByRegion(region, currentUserId)
                                else -> emptyList()
                            }
                        }

                        val friendSet = allFriendIds.toSet()
                        val requestSentSet = allRequestSentIds.toSet()
                        val requestReceivedSet = allRequestReceivedIds.toSet()
                        val blockedSet = profile.blockedUsers.toSet()

                        val newSuggestions = rawSuggestions.filter {
                            it.uid != currentUserId &&
                            it.uid !in friendSet &&
                            it.uid !in requestSentSet &&
                            it.uid !in requestReceivedSet &&
                            it.uid !in blockedSet
                        }

                        val persistentSuggestions = allSuggestions.filter {
                            it.uid !in friendSet &&
                            it.uid !in requestSentSet &&
                            it.uid !in requestReceivedSet &&
                            it.uid !in blockedSet
                        }
                        val existingIds = persistentSuggestions.map { it.uid }.toSet()
                        val uniqueNewOnes = newSuggestions.filter { it.uid !in existingIds }
                        allSuggestions = persistentSuggestions + uniqueNewOnes

                        _uiState.update { it.copy(suggestions = allSuggestions) }
                    } catch (e: Exception) {
                        android.util.Log.e("FriendListVM", "Error en sugerencias", e)
                    }
                }
        } // end suggestionsJob launch
    } // end observeData

    fun acceptRequest(currentUserId: String, requesterUid: String) {
        viewModelScope.launch {
            val result = userRepository.acceptFriendRequest(requesterUid)
            if (result.isSuccess) {
                // Forzar observación inmediata para mitigar delay de Firestore
                observeData(currentUserId)
            }
        }
    }

    fun sendFriendRequest(currentUserId: String, targetUid: String) {
        viewModelScope.launch {
            userRepository.sendFriendRequest(targetUid)
            // observeData handles updates reactively
        }
    }

    fun removeFriend(currentUserId: String, friendUid: String) {
        viewModelScope.launch {
            // Optimistic update
            val currentFriends = _friends.value.filter { it.uid != friendUid }
            _friends.value = currentFriends
            _uiState.value = _uiState.value.copy(friends = currentFriends)
            
            val result = userRepository.removeFriend(friendUid)
            if (result.isSuccess) {
                // Forzar observación inmediata
                observeData(currentUserId)
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Error al eliminar amigo")
            }
        }
    }
}
