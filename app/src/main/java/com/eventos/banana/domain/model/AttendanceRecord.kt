package com.eventos.banana.domain.model

data class AttendanceRecord(
    val id: String = "",
    val eventId: String = "",
    val userId: String = "",
    val checkpoints: List<LocationCheckpoint> = emptyList(),
    val isVerified: Boolean = false,  // ✅ Cumplió requisitos (50m, 10min)
    val totalMinutesInRange: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class LocationCheckpoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    val distanceToEvent: Float = 0f  // Distancia en metros al evento
)
