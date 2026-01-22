package com.eventos.banana.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.FeedRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.FeedPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val posts: List<FeedPost> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isUploading: Boolean = false,
    val creatorNickname: String = "Organizador",
    val creatorId: String = ""
)

class FeedViewModel(
    private val eventId: String,
    private val repository: FeedRepository = FeedRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val eventRepository: com.eventos.banana.data.repository.EventRepository = com.eventos.banana.data.repository.EventRepository()
) : ViewModel() {

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
                    posts.forEachIndexed { index, post ->
                        android.util.Log.d("FeedViewModel", "  ViewModel Post $index: content='${post.content}'")
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        posts = posts,
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

    fun createPost(userId: String, content: String, imageBytes: ByteArray?) {
        if (content.isBlank() && imageBytes == null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)

            // Obtener nickname actualizado
            val userProfile = userRepository.getUserProfile(userId)
            val nickname = userProfile?.nickname ?: "Usuario"

            val post = FeedPost(
                eventId = eventId,
                userId = userId,
                userNickname = nickname,
                content = content,
                isUserVerified = userProfile?.isVerified ?: false
            )

            val result = repository.createPost(post, imageBytes)

            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = result.exceptionOrNull()?.message
                )
            } else {
                _uiState.value = _uiState.value.copy(isUploading = false, error = null)
                
                // 🔔 NOTIFICAR A PARTICIPANTES
                try {
                    val eventResult = eventRepository.getEventById(eventId)
                    if (eventResult.isSuccess) {
                        val event = eventResult.getOrNull()!!
                        
                        // Lista de destinatarios: Creador + Participantes - AutorDelPost
                        val recipients = (event.approvedParticipants + event.creatorId)
                            .distinct()
                            .filter { it != userId }
                        
                        android.util.Log.d("FeedViewModel", "Notifying ${recipients.size} users about new wall post")
                        
                        val notifRepository = com.eventos.banana.data.repository.NotificationRepository()
                        val userRepository = com.eventos.banana.data.repository.UserRepository()
                        
                        recipients.forEach { recipientUid ->
                            // Check preference (fetch profile mostly cached)
                            val profile = userRepository.getUserProfile(recipientUid)
                            if (profile?.notifyEventWall == true) {
                                val notification = com.eventos.banana.domain.model.AppNotification(
                                    userId = recipientUid,
                                    title = "Nuevo mensaje en ${event.title}",
                                    message = "$nickname: ${(if(content.length > 30) content.take(30)+"..." else content)}",
                                    type = com.eventos.banana.domain.model.NotificationType.EVENT_UPDATE, // Reuse type for simplicity or add WALL_POST
                                    eventId = eventId,
                                    read = false
                                )
                                notifRepository.sendNotification(notification)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FeedViewModel", "Failed to send notifications", e)
                }
            }
        }
    }
}
