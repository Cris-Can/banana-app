package com.eventos.banana.domain.model

data class AppNotification(
    val id: String = "",
    val userId: String = "",        // 👈 a quién va
    val title: String = "",
    val message: String = "",
    val eventId: String? = null,    // opcional
    val createdAt: Long = 0L,
    val read: Boolean = false,
    val type: NotificationType = NotificationType.GENERIC
)
