package com.eventos.banana.ui.profile

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
    val requests: List<UserProfile> = emptyList()
)

class FriendListViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendListUiState())
    val uiState: StateFlow<FriendListUiState> = _uiState

    fun loadFriends(uid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val profile = userRepository.getUserProfile(uid)
                if (profile != null) {
                    val friends = profile.friends.map { friendId ->
                        userRepository.getUserProfile(friendId, forceRefresh = true) ?: UserProfile(uid = friendId, nickname = "Usuario", email = "")
                    }
                    val requests = profile.friendRequestsReceived.map { senderId ->
                        userRepository.getUserProfile(senderId, forceRefresh = true) ?: UserProfile(uid = senderId, nickname = "Usuario", email = "")
                    }
                    _uiState.value = FriendListUiState(
                        isLoading = false,
                        friends = friends,
                        requests = requests
                    )
                } else {
                    _uiState.value = FriendListUiState(isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = FriendListUiState(isLoading = false)
            }
        }
    }

    fun acceptRequest(currentUid: String, otherUid: String) {
        viewModelScope.launch {
            userRepository.acceptFriendRequest(currentUid, otherUid)
            loadFriends(currentUid) // Reload
        }
    }
}
