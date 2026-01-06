package com.eventos.banana.domain.model

data class Event(
    val id: String = "",
    val creatorId: String = "",

    // Información básica
    val title: String = "",
    val description: String = "",
    val category: String = "", // fiesta, deporte, paseo, etc.

    // Ubicación (visible)
    val country: String = "Chile",
    val region: String = "",
    val commune: String = "",

    // Tiempo
    val eventTimestamp: Long = 0L,   // fecha del evento
    val createdAt: Long = 0L,        // fecha creación

    // Capacidad
    val maxParticipants: Int = 0,

    // Reglas
    val minScoreRequired: Int = 0,
    val approvalRequired: Boolean = true,

    // Estado
    val status: EventStatus = EventStatus.OPEN,

    // Participación
    val applicants: List<String> = emptyList(),
    val accepted: List<String> = emptyList(),
    val rejected: List<String> = emptyList()
)
