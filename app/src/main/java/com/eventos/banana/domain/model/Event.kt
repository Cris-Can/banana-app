package com.eventos.banana.domain.model

data class Event(
    val id: String = "",
    val creatorId: String = "",
    val imageUrl: String? = null,

    val title: String = "",
    val description: String = "",
    val category: String = "",
    val eventType: EventType = EventType.OTRO,
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
    
    // 🌍 PUBLIC EVENTS (Update)
    val isPublic: Boolean = false, // True = Entrada libre, sin aprobación, ubicación visible

    val approvalRequired: Boolean = true,
    val joinQuestions: List<JoinQuestion> = emptyList(),

    val status: EventStatus = EventStatus.OPEN,
    val cancelledAt: Long? = null,
    val cancelReason: String? = null,

    val approvedParticipants: List<String> = emptyList(),
    val pendingRequests: List<JoinRequest> = emptyList(),
    val rejectedParticipants: List<String> = emptyList(),
    
    // ⭐ SISTEMA DE PUNTUACIÓN (Round 11)
    val minimumScore: Double? = null,    // null = sin restricción, ej: 3.5
    val ratingDeadline: Long? = null,    // eventTimestamp + 5 días
    val canBeRated: Boolean = false,      // true si ya finalizó y se puede puntuar
    
    // 💎 MONETIZACIÓN (Round 42)
    val isBoosted: Boolean = false,       // Evento destacado en feed
    val boostExpiry: Long = 0L,           // Expiración del boost
    
    // 🌍 GEOHASHING (Round 52)
    val geohash: String? = null           // Indexed field for radius queries
) {
    // Helper para verificar si un usuario puede unirse según su score
    fun canUserJoin(userAverageRating: Double, userRatingCount: Int): Boolean {
        // Si no hay restricción, todos pueden
        if (minimumScore == null) return true
        
        // Usuarios sin rating pueden unirse (considerados neutrales)
        if (userRatingCount == 0) return true
        
        // Verificar si cumple el mínimo
        return userAverageRating >= minimumScore
    }
    
    // Helper para verificar si aún está en plazo de puntuación
    fun isRatingWindowOpen(): Boolean {
        if (!canBeRated || ratingDeadline == null) return false
        return System.currentTimeMillis() <= ratingDeadline
    }
}
