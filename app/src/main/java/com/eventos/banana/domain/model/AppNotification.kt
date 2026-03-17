package com.eventos.banana.domain.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class AppNotification(
    val id: String = "",
    val userId: String = "",        // 👈 a quién va
    val title: String = "",
    val message: String = "",
    val eventId: String? = null,    // opcional
    val conversationId: String? = null, // 👈 para chats y muro
    val fromUserId: String? = null, // 👈 sender id (for friend requests etc)
    @ServerTimestamp
    val createdAt: Date? = null,
    val read: Boolean = false,
    val type: NotificationType = NotificationType.GENERIC
)
