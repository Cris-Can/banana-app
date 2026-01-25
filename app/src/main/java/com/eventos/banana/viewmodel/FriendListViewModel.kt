package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FriendListUiState(
    val isLoading: Boolean = false,
    val friends: List<UserProfile> = emptyList(),
    val requests: List<UserProfile> = emptyList(),
    val suggestions: List<UserProfile> = emptyList(),
    val searchResults: List<UserProfile> = emptyList(),
    val errorMessage: String? = null
)

class FriendListViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendListUiState())
    val uiState: StateFlow<FriendListUiState> = _uiState

    // Track original lists for local search
    private var allFriends: List<UserProfile> = emptyList()
    private var allRequests: List<UserProfile> = emptyList()
    private var allSuggestions: List<UserProfile> = emptyList()

    fun loadData(currentUserId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 1. Get Current User Profile to know friends/requests lists
                val currentUser = userRepository.getUserProfile(currentUserId)
                if (currentUser == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Usuario no encontrado")
                    return@launch
                }

                // 2. Fetch Friends
                val friendIds = currentUser.friends
                val friendsList = friendIds.mapNotNull { userRepository.getUserProfile(it) }

                // 3. Fetch Requests (Received)
                val requestIds = currentUser.friendRequestsReceived
                val requestsList = requestIds.mapNotNull { userRepository.getUserProfile(it) }

                // 4. Fetch Suggestions (Same Region, Exclude Friends/Requests)
                val region = currentUser.region ?: ""
                val suggestionsList = if (region.isNotBlank()) {
                    userRepository.getUsersByRegion(region, currentUserId)
                        .filter { user ->
                            user.uid !in friendIds && 
                            user.uid !in requestIds &&
                            user.uid !in currentUser.friendRequestsSent // Don't suggest if already requested
                        }
                } else {
                    emptyList()
                }

                allFriends = friendsList
                allRequests = requestsList
                allSuggestions = suggestionsList

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    friends = friendsList,
                    requests = requestsList,
                    suggestions = suggestionsList
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun searchUsers(query: String) {
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

        // Local search filter
        val filteredFriends = allFriends.filter { it.nickname.contains(query, ignoreCase = true) }
        val filteredRequests = allRequests.filter { it.nickname.contains(query, ignoreCase = true) }
        val filteredSuggestions = allSuggestions.filter { it.nickname.contains(query, ignoreCase = true) }
        
        // TODO: Could also implement global search here if needed
        
        _uiState.value = _uiState.value.copy(
            friends = filteredFriends,
            requests = filteredRequests,
            suggestions = filteredSuggestions
        )
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
}
