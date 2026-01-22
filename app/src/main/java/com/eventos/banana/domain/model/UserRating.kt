package com.eventos.banana.domain.model

data class UserRating(
    val ratingId: String = "",
    val eventId: String = "",
    val eventType: EventType = EventType.OTRO,
    val fromUserId: String = "",    // Quien califica
    val toUserId: String = "",      // Quien recibe la calificación
    
    // Score simple 1-5 estrellas
    val score: Double = 0.0,        // Valor principal (1.0 - 5.0)
    
    // Comentario opcional (solo Premium)
    val comment: String? = null,
    
    // Metadata
    val timestamp: Long = 0L,
    val canEditUntil: Long = 0L,    // timestamp + 10 minutos
    val isEdited: Boolean = false
) {
    // Helper para verificar si aún se puede editar
    fun canEdit(): Boolean {
        return System.currentTimeMillis() < canEditUntil
    }
    
    // Helper para formatear a Map para Firestore
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "ratingId" to ratingId,
            "eventId" to eventId,
            "eventType" to eventType.name,
            "fromUserId" to fromUserId,
            "toUserId" to toUserId,
            "score" to score,
            "comment" to comment,
            "timestamp" to timestamp,
            "canEditUntil" to canEditUntil,
            "isEdited" to isEdited
        )
    }
    
    companion object {
        // Helper para crear desde Firestore
        fun fromMap(map: Map<String, Any?>): UserRating {
            return UserRating(
                ratingId = map["ratingId"] as? String ?: "",
                eventId = map["eventId"] as? String ?: "",
                eventType = try {
                    EventType.valueOf(map["eventType"] as? String ?: "OTRO")
                } catch (e: Exception) {
                    EventType.OTRO
                },
                fromUserId = map["fromUserId"] as? String ?: "",
                toUserId = map["toUserId"] as? String ?: "",
                score = (map["score"] as? Number)?.toDouble() ?: 0.0,
                comment = map["comment"] as? String,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                canEditUntil = (map["canEditUntil"] as? Number)?.toLong() ?: 0L,
                isEdited = map["isEdited"] as? Boolean ?: false
            )
        }
    }
}
