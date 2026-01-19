package com.eventos.banana.domain.model

data class Event(
    val id: String = "",
    val creatorId: String = "",
    val imageUrl: String? = null,

    val title: String = "",
    val description: String = "",
    val category: String = "",
    val archivedAt: Long? = null,

    val country: String = "Chile",
    val region: String = "",
    val commune: String = "",

    // 🔐 SOLO PARA ACEPTADOS / CREADOR
    // 🔔 NUEVO: preferencia de notificaciones
    val notifyEventsByCommune: Boolean = false,
    val fcmToken: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    
    // 🗺️ Ubicación exacta (solo visible para creador y aprobados)
    val exactLatitude: Double? = null,
    val exactLongitude: Double? = null,
    val exactAddress: String? = null,

    val eventTimestamp: Long = 0L,
    val createdAt: Long = 0L,
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    @get:com.google.firebase.firestore.PropertyName("archived")
    @set:com.google.firebase.firestore.PropertyName("archived")
    var isArchived: Boolean = false,        // evento ya terminó
    val expiresAt: Long? = null,            // cuándo se elimina definitivamente



    val maxParticipants: Int = 0,

    val approvalRequired: Boolean = true,
    val joinQuestions: List<JoinQuestion> = emptyList(),

    val status: EventStatus = EventStatus.OPEN,
    val cancelledAt: Long? = null,
    val cancelReason: String? = null,

    val approvedParticipants: List<String> = emptyList(),
    val pendingRequests: List<JoinRequest> = emptyList(),
    val rejectedParticipants: List<String> = emptyList()
)
