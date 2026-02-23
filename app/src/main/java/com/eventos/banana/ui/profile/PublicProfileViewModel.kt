package com.eventos.banana.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.crashlytics.FirebaseCrashlytics

enum class FriendStatus {
    NONE,
    FRIEND,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    SELF // Viewing own profile via public link
}

data class PublicProfileUiState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val friendStatus: FriendStatus = FriendStatus.NONE,
    val error: String? = null
)

@HiltViewModel(assistedFactory = PublicProfileViewModel.Factory::class)
class PublicProfileViewModel @AssistedInject constructor(
    @Assisted private val targetUid: String,
    val userRepository: UserRepository,
    val authRepository: AuthRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(targetUid: String): PublicProfileViewModel
    }

    private val _uiState = MutableStateFlow(PublicProfileUiState())
    val uiState: StateFlow<PublicProfileUiState> = _uiState

    init {
        loadProfile(targetUid)
    }


    fun loadProfile(targetUid: String) {
        val currentUid = authRepository.currentUid()
        
        if (targetUid == currentUid) {
             // Viewing self, though usually redirected to ProfileScreen
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                android.util.Log.d("PublicProfileVM", "Loading profile for $targetUid")
                val profile = userRepository.getUserProfile(targetUid)
                
                if (profile != null) {
                    android.util.Log.d("PublicProfileVM", "Profile found: ${profile.nickname}")
                    
                    // 👁️ TRACK VIEW
                    if (currentUid != null && currentUid != targetUid) {
                        launch { // Fire and forget inside scope, don't block UI
                            try {
                                userRepository.recordProfileView(currentUid, targetUid)
                            } catch (e: Exception) {
                                android.util.Log.e("PublicProfileVM", "Error recording view", e)
                            }
                        }
                    }

                    val status = calculateFriendStatus(currentUid, profile)
                    _uiState.value = PublicProfileUiState(
                        isLoading = false,
                        profile = profile,
                        friendStatus = status
                    )
                } else {
                    // 👻 GHOST PROFILE FALLBACK
                    // If profile doesn't exist in DB, show a generated one to prevent UI error
                    android.util.Log.w("PublicProfileVM", "Profile not found for $targetUid. Using Ghost Profile.")
                    FirebaseCrashlytics.getInstance().recordException(Exception("Ghost Profile created for missing user uid: $targetUid"))
                    val ghostProfile = UserProfile(
                        uid = targetUid,
                        nickname = "Usuario",
                        email = "",
                        aboutMe = "Perfil no disponible"
                    )
                    val status = calculateFriendStatus(currentUid, ghostProfile)
                    _uiState.value = PublicProfileUiState(
                        isLoading = false,
                        profile = ghostProfile,
                        friendStatus = status
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("PublicProfileVM", "Error loading profile", e)
                _uiState.value = PublicProfileUiState(isLoading = false, error = "Error de conexión")
            }
        }
    }

    private fun calculateFriendStatus(currentUid: String?, targetProfile: UserProfile): FriendStatus {
        if (currentUid == null) return FriendStatus.NONE
        if (currentUid == targetProfile.uid) return FriendStatus.SELF

        return when {
            targetProfile.friends.contains(currentUid) -> FriendStatus.FRIEND
            targetProfile.friendRequestsReceived.contains(currentUid) -> FriendStatus.REQUEST_SENT
            targetProfile.friendRequestsSent.contains(currentUid) -> FriendStatus.REQUEST_RECEIVED
            else -> FriendStatus.NONE
        }
    }

    fun sendFriendRequest(targetUid: String) {
        val currentUid = authRepository.currentUid() ?: return
        viewModelScope.launch {
            try {
                userRepository.sendFriendRequest(currentUid, targetUid)
                // Reload or check optimistic update
                loadProfile(targetUid) 
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun acceptFriendRequest(requesterUid: String) {
        val currentUid = authRepository.currentUid() ?: return
        viewModelScope.launch {
             try {
                 userRepository.acceptFriendRequest(currentUid, requesterUid)
                 loadProfile(requesterUid)
             } catch(e: Exception) {
                 // Error
             }
        }
    }
    fun blockUser(targetUid: String) {
        val currentUid = authRepository.currentUid() ?: return
        viewModelScope.launch {
            try {
                userRepository.blockUser(currentUid, targetUid)
            } catch (e: Exception) {
                // Log
            }
        }
    }

    fun unblockUser(targetUid: String) {
        val currentUid = authRepository.currentUid() ?: return
        viewModelScope.launch {
            try {
                userRepository.unblockUser(currentUid, targetUid)
            } catch (e: Exception) {
                // Log
            }
        }
    }

    fun reportUser(reportedUid: String, reason: String) {
        val currentUid = authRepository.currentUid() ?: return
        viewModelScope.launch {
            try {
                userRepository.reportUser(currentUid, reportedUid, reason)
            } catch (e: Exception) {
               // Log
            }
        }
    }
}
