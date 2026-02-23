package com.eventos.banana.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.MessageRepository
import com.eventos.banana.domain.model.Conversation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    val messageRepository: MessageRepository
) : ViewModel() {

    fun observeConversations(userId: String): StateFlow<List<Conversation>> {
        return messageRepository.observeConversations(userId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    suspend fun deleteConversation(conversationId: String) {
        messageRepository.deleteConversation(conversationId)
    }
}
