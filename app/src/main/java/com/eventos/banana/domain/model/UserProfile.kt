package com.eventos.banana.domain.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val nickname: String = "",
    val region: String? = null,
    val commune: String? = null,

    // 🔔 NUEVO: preferencia de notificaciones
    val notifyEventsByCommune: Boolean = false,

    val score: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
