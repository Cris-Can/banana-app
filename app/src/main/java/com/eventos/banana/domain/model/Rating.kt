package com.eventos.banana.domain.model

data class Rating(
    val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val eventId: String = "",
    val interest: String = "",         // Nuevo: Interés específico (Deportes, Música, etc.)
    val score: Int = 0, // 1 to 5
    val comment: String = "",
    val attendanceVerified: Boolean = false,  // Nuevo: Verificación de asistencia
    val createdAt: Long = System.currentTimeMillis()
)
