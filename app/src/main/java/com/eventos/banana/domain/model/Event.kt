package com.eventos.banana.domain.model

data class Event(
    val id: String = "",
    val creatorId: String = "",

    // Información básica
    val title: String = "",
    val description: String = "",
    val category: String = "",

    // Ubicación
    val country: String = "Chile",
    val region: String = "",
    val commune: String = "",

    // Tiempo
    val eventTimestamp: Long = 0L,
    val createdAt: Long = 0L,

    // Capacidad
    val maxParticipants: Int = 0,

    // Configuración de acceso
    val approvalRequired: Boolean = true,
    val joinQuestions: List<JoinQuestion> = emptyList(),

    // Estado
    val status: EventStatus = EventStatus.OPEN,

    // Participación (ÚNICA FUENTE DE VERDAD)
    val approvedParticipants: List<String> = emptyList(),
    val pendingRequests: List<JoinRequest> = emptyList(),
    val rejectedParticipants: List<String> = emptyList()
)
