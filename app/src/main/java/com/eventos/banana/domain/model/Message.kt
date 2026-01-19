package com.eventos.banana.domain.model

data class Conversation(
    val id: String = "",
    val participants: List<String> = emptyList(),  // UIDs de los 2 usuarios
    val participantNicknames: Map<String, String> = emptyMap(),  // uid -> nickname
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Map<String, Int> = emptyMap(),  // uid -> cantidad no leídas
    val createdAt: Long = System.currentTimeMillis()
)

data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false
)
