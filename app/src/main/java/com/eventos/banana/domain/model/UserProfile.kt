package com.eventos.banana.domain.model

import com.google.firebase.firestore.PropertyName

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val nickname: String = "",
    
    // 🎂 DATOS PERSONALES (Round 13)
    val birthDate: Long? = null,  // Timestamp de fecha de nacimiento
    val age: Int? = null,          // Edad calculada (se actualiza periódicamente)
    
    val region: String? = null,
    val commune: String? = null,
    val notifyEventsByCommune: Boolean = false,
    val notifyEventWall: Boolean = true, // 🔔 Avisa nuevos mensajes en Muro
    val fcmToken: String? = null,
    val appTheme: String = "BANANA", // 🎨 Options: BANANA, DARK, LIGHT
    @get:PropertyName("isVerified") @set:PropertyName("isVerified") var isVerified: Boolean = false, // 👈 A23 Account verification

    val score: Int = 0,
    
    // ⭐ SISTEMA DE PUNTUACIÓN (Round 11)
    val ratingSum: Double = 0.0,
    val ratingCount: Int = 0,
    
    // 💎 PREMIUM FLAG (Round 11)
    val isPremium: Boolean = false, // Se obtiene de subscriptionType, pero cache para queries
    
    // 🎨 SOCIAL (A20)
    val aboutMe: String = "",
    val interests: List<String> = emptyList(),
    val profilePictureUrl: String? = null, // Foto de perfil principal
    val photos: List<String> = emptyList(), // URLs de fotos (galería)

    // 🤝 AMIGOS (A20)
    val friends: List<String> = emptyList(),              // UIDs de amigos confirmados
    val friendRequestsReceived: List<String> = emptyList(), // UIDs de solicitudes entrantes
    val friendRequestsSent: List<String> = emptyList(),     // UIDs de solicitudes enviadas
    
    // 💎 SUSCRIPCIÓN & LÍMITES (A28)
    var subscriptionType: SubscriptionType = SubscriptionType.PREMIUM, // Default PREMIUM for early adopters
    var currentCycleStartDate: Long = System.currentTimeMillis(),
    var eventsCreatedInCycle: Int = 0,
    var joinRequestsInCycle: Int = 0,

    val createdAt: Long = System.currentTimeMillis()
) {
    // Computed property: average rating
    val averageRating: Double
        get() = if (ratingCount > 0) {
            (ratingSum / ratingCount * 10).toInt() / 10.0 // Round to 1 decimal
        } else {
            0.0
        }
    
    // Helper para obtener badge según score
    fun getRatingBadge(): String {
        return when {
            ratingCount == 0 -> "🆕" // Nuevo Usuario
            averageRating >= 4.5 -> "🏆" // Top Banana
            averageRating >= 4.0 -> "🥇" // Confiable
            averageRating >= 3.0 -> "✅" // Bueno
            else -> "⚠️" // En Desarrollo
        }
    }
    
    fun getRatingBadgeText(): String {
        return when {
            ratingCount == 0 -> "Nuevo Usuario"
            averageRating >= 4.5 -> "Top Banana"
            averageRating >= 4.0 -> "Confiable"
            averageRating >= 3.0 -> "Bueno"
            else -> "En Desarrollo"
        }
    }
}

enum class SubscriptionType {
    FREE,
    PREMIUM
}
