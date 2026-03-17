package com.eventos.banana.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.FeedRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.FeedPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.data.repository.AuthRepository

data class FeedUiState(
    val posts: List<FeedPost> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isUploading: Boolean = false,
    val creatorNickname: String = "Organizador",
    val creatorId: String = "",
    val replyingTo: FeedPost? = null
)



@HiltViewModel(assistedFactory = FeedViewModel.Factory::class)
class FeedViewModel @AssistedInject constructor(
    @Assisted private val eventId: String,
    private val repository: FeedRepository,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: com.eventos.banana.data.repository.NotificationRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(eventId: String): FeedViewModel
    }


    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState

    init {
        loadPosts()
        loadCreatorInfo()
    }

    private fun loadCreatorInfo() {
        viewModelScope.launch {
            val result = eventRepository.getEventById(eventId)
            result.onSuccess { event ->
                val profile = userRepository.getUserProfile(event.creatorId)
                _uiState.value = _uiState.value.copy(
                    creatorNickname = profile?.nickname ?: "Organizador",
                    creatorId = event.creatorId
                )
            }
        }
    }

    private fun loadPosts() {
        viewModelScope.launch {
            android.util.Log.d("FeedViewModel", "🔵 Starting to collect posts...")
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.getPosts(eventId).collect { posts ->
                    android.util.Log.d("FeedViewModel", "🟢 Collected ${posts.size} posts from repository")
                    
                    // 🛡️ BLOCKING LOGIC
                    val currentUid = authRepository.currentUid()
                    val blockedUsers = if (currentUid != null) {
                        userRepository.getUserProfile(currentUid)?.blockedUsers ?: emptyList()
                    } else {
                        emptyList()
                    }
                    
                    val filteredPosts = posts.filter { post ->
                        !blockedUsers.contains(post.userId)
                    }

                    android.util.Log.d("FeedViewModel", "🛡️ Filtered ${posts.size - filteredPosts.size} blocked posts")

                    filteredPosts.forEachIndexed { index, post ->
                        android.util.Log.d("FeedViewModel", "  ViewModel Post $index: content='${post.content}'")
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        posts = filteredPosts,
                        isLoading = false,
                        error = null
                    )
                    android.util.Log.d("FeedViewModel", "✅ UI State updated with ${_uiState.value.posts.size} posts")
                }
            } catch (e: Exception) {
                android.util.Log.e("FeedViewModel", "❌ Error collecting posts", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No se pudieron cargar los mensajes: ${e.message}"
                )
            }
        }
    }

    fun setReplyingTo(post: FeedPost?) {
        _uiState.value = _uiState.value.copy(replyingTo = post)
    }

    fun cancelReply() {
        _uiState.value = _uiState.value.copy(replyingTo = null)
    }

    fun createPost(userId: String, content: String, imageBytes: ByteArray?) {
        if (content.isBlank() && imageBytes == null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)

            // Obtener nickname actualizado
            val userProfile = userRepository.getUserProfile(userId)
            val nickname = userProfile?.nickname ?: "Usuario"

            val replyingTo = _uiState.value.replyingTo

            val post = FeedPost(
                eventId = eventId,
                userId = userId,
                userNickname = nickname,
                content = content,
                isUserVerified = userProfile?.isVerified ?: false,
                replyToId = replyingTo?.id,
                replyToNickname = replyingTo?.userNickname,
                replyToContent = replyingTo?.content?.let { if (it.length > 50) it.take(47) + "..." else it },
                replyToUserId = replyingTo?.userId
            )

            val result = repository.createPost(post, imageBytes)

            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = result.exceptionOrNull()?.message
                )
            } else {
                _uiState.value = _uiState.value.copy(isUploading = false, error = null, replyingTo = null)
                
                // 🔔 NOTIFICAR AL USUARIO RESPONDIDO (Push)
                if (replyingTo != null && replyingTo.userId != userId) {
                    android.util.Log.d("FeedViewModel", "🔔 Sending response notification to user: ${replyingTo.userId}")
                    notificationRepository.sendNotification(
                        com.eventos.banana.domain.model.AppNotification(
                            userId = replyingTo.userId,
                            fromUserId = userId,
                            title = "¡Te respondieron en el muro! 💬",
                            message = "$nickname respondió a tu mensaje",
                            eventId = eventId,
                            conversationId = eventId,
                            type = com.eventos.banana.domain.model.NotificationType.EVENT_WALL_POST
                        )
                    )
                } else if (replyingTo?.userId == userId) {
                    android.util.Log.d("FeedViewModel", "🚫 Notification skipped: Self-reply")
                }
                
                android.util.Log.d("FeedViewModel", "Post created successfully.")
            }
        }
    }
    fun blockUser(targetUid: String) {
        viewModelScope.launch {
            try {
                // Get current user ID (assuming we can get it from repository or pass it in)
                // For this VM, we might need to pass currentUserId to the function or get it from Auth
                // Since FeedViewModel doesn't have AuthRepository injected, we'll rely on the caller passing ID 
                // OR we inject AuthRepository.
                // Simpler: FeedViewModel is often scoped to Event, let's pass currentUserId to the function
            } catch (e: Exception) { }
        }
    }
    
    // Better approach: Since we already call createPost with userId, we can do the same here.
    fun blockUser(currentUid: String, targetUid: String) {
         viewModelScope.launch {
             try {
                 userRepository.blockUser(currentUid, targetUid)
                 // Optimistically filter posts from blocked user
                 _uiState.value = _uiState.value.copy(
                     posts = _uiState.value.posts.filter { it.userId != targetUid }
                 )
             } catch (e: Exception) {
                 // Log
             }
         }
    }

    fun reportPost(currentUid: String, post: FeedPost, reason: String) {
        viewModelScope.launch {
            try {
                userRepository.reportUser(currentUid, post.userId, "POST_REPORT: ${post.id} - $reason")
            } catch (e: Exception) { }
        }
    }
}
