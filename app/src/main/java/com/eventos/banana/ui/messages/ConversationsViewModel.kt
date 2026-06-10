package com.eventos.banana.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.MessageRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.Conversation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _profiles = MutableStateFlow<Map<String, Pair<String, String?>>>(emptyMap())
    val profiles: StateFlow<Map<String, Pair<String, String?>>> = _profiles.asStateFlow()

    fun observeConversations(userId: String): StateFlow<List<Conversation>> {
        return messageRepository.observeConversations(userId)
            .onEach { conversations ->
                fetchProfilesForConversations(userId, conversations)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    private fun fetchProfilesForConversations(currentUserId: String, conversations: List<Conversation>) {
        viewModelScope.launch {
            val otherUserIds = conversations.mapNotNull { conv ->
                conv.participants.firstOrNull { it != currentUserId }
            }.distinct()
            
            val existing = _profiles.value
            val newIds = otherUserIds.filter { it !in existing }
            if (newIds.isNotEmpty()) {
                val loadedUsers = userRepository.getUsers(newIds)
                val newProfiles = loadedUsers.associate { user ->
                    user.uid to Pair(user.nickname, user.profilePictureUrl)
                }
                _profiles.value = existing + newProfiles
            }
        }
    }

    private val _unreadMessagesCount = MutableStateFlow(0)
    val unreadMessagesCount: StateFlow<Int> = _unreadMessagesCount

    fun startUnreadMessagesObservation(userId: String) {
        viewModelScope.launch {
            messageRepository.observeConversations(userId).collect { list ->
                _unreadMessagesCount.value = list.sumOf { it.unreadCount[userId] ?: 0 }
            }
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        messageRepository.deleteConversation(conversationId)
    }
}
