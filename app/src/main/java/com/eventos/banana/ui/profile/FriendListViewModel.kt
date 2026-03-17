package com.eventos.banana.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

                val friendsList = userRepository.getUsers(allFriendIds.take(pageSize))
                
                _friends.value = friendsList
                allFriends = friendsList
                currentPage = 1
                hasMoreFriends.value = pageSize < allFriendIds.size

                val requestsList = userRepository.getUsers(allRequestReceivedIds)

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
                
                val newFriends = userRepository.getUsers(chunkIds)
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
                
                // Filter out self and existing friends from search results to avoid duplicates
                val finalSearchResults = globalResults.filter { user ->
                    user.uid != currentUserId &&
                    user.uid !in allFriendIds &&
                    user.uid !in allRequestReceivedIds &&
                    user.uid !in allRequestSentIds
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

    fun acceptRequest(currentUserId: String, requesterUid: String) {
        viewModelScope.launch {
            userRepository.acceptFriendRequest(currentUserId, requesterUid)
            loadData(currentUserId) // Reload to refresh lists
        }
    }

    fun sendFriendRequest(currentUserId: String, targetUid: String) {
        viewModelScope.launch {
            userRepository.sendFriendRequest(currentUserId, targetUid)
            // Ideally optimistic update, but simple reload works
            loadData(currentUserId)
        }
    }

    fun removeFriend(currentUserId: String, friendUid: String) {
        viewModelScope.launch {
            userRepository.removeFriend(currentUserId, friendUid)
            loadData(currentUserId)
        }
    }
}
