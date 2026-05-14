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
    val country: String? = null,
    val latitude: Double? = null, // 🌍 Global Expansion
    val longitude: Double? = null, // 🌍 Global Expansion
    val geohash: String? = null, // 🌍 Geohashing (Round 52)
    val searchRadiusKm: Int = 20, // 🌍 Radius Search (Default 20km)
    val notifyEventsByCommune: Boolean = true,
    val notifyEventWall: Boolean = true, // 🔔 Avisa nuevos mensajes en Muro
    val notifyByInterest: Boolean = true, // 🎯 Avisa por gustos e intereses
    val fcmToken: String? = null,
    val appTheme: String = "BANANA", // 🎨 Options: BANANA, DARK, LIGHT
    var isVerified: Boolean = false, // 👈 A23 Account verification

    val score: Int = 0,
    
    // ⭐ SISTEMA DE PUNTUACIÓN (Round 11)
    val ratingSum: Double = 0.0,
    val ratingCount: Int = 0,
    val averageScore: Double = 0.0,
    
    // 💎 PREMIUM FLAG (Round 11)
    val isGoldStored: Boolean = false,
    val isPremiumStored: Boolean = false,
    val isFounder: Boolean = false, // 🚀 Early Adopter Badge
    
    // 🎨 SOCIAL (A20)
    val aboutMe: String = "",
    val interests: List<String> = emptyList(),
    val profilePictureUrl: String? = null, // Foto de perfil principal
    val coverPhotoUrl: String? = null, // 🖼️ Foto de portada (Facebook style)
    val photos: List<String> = emptyList(), // URLs de fotos (galería)
    
    // 👁️ VISTAS DE PERFIL
    val profileViews: Int = 0,
    val recentViewers: List<String> = emptyList(), // UIDs de usuarios que vieron el perfil recientemente

    // 🤝 AMIGOS (A20)
    val friends: List<String> = emptyList(),              // UIDs de amigos confirmados
    val friendCount: Int = 0,
    val friendRequestsReceived: List<String> = emptyList(), // UIDs de solicitudes entrantes
    val pendingRequestsReceivedCount: Int = 0,
    val friendRequestsSent: List<String> = emptyList(),     // UIDs de solicitudes enviadas
    val pendingRequestsSentCount: Int = 0,
    
    // 🛡️ TRUST & SAFETY (Round 49)
    val blockedUsers: List<String> = emptyList(),     // UIDs de usuarios bloqueados
    
    // 👮 ADMIN & MODERATION (Round 69)
    val admin: Boolean = false, // 🔑 Acceso al Panel de Control
    val isBanned: Boolean = false, // 🚫 Usuario baneado (no puede loguear/interactuar)
    
    // 💾 SAVED EVENTS (A30)
    val savedEventIds: List<String> = emptyList(),
    
    // 💎 SUSCRIPCIÓN & LÍMITES (A28)
    var subscriptionType: String = "FREE", // Changed to String for Firestore compatibility
    val subscriptionExpiry: Long = 0, // 0 = Lifetime or Expired
    
    var currentCycleStartDate: Long = System.currentTimeMillis(),
    var eventsCreatedInCycle: Int = 0,
    var joinRequestsInCycle: Int = 0,

    // 📺 ADS MONETIZATION (Round 43)
    val adEventsUnlocked: Int = 0, // Number of extra events unlocked this cycle
    val adsWatchedProgress: Int = 0, // Progress towards next unlock (0, 1) - resets to 0 after unlock
    
    // 🔔 A29 NOTIFICATIONS (Round 35)
    val subscribedCategories: List<String> = emptyList(), // Topics: "events_DEPORTES", etc.

    // 📊 ESTADÍSTICAS DE ASISTENCIA (Round 14)
    val eventsRequestedCount: Int = 0, // Eventos a los que pidió asistir
    val eventsAttendedCount: Int = 0,  // Eventos a los que asistió realmente (Check-in/NFC)
    val eventsCreatedLifetime: Int = 0, // 🆕 Total eventos organizados (separado de ciclo)

    val invitationCode: String? = null, // 🎟️ Cupón para ser Founder (uso único)

    // 🔄 MIGRACIÓN LEGACY (guard de idempotencia — Round 70)
    // Evita re-ejecutar el check de upgrade de primeros 40 founders en cada login.
    // Una vez en true, el bloque `checkAndUpgradeLegacyUser` retorna inmediatamente.
    val isLegacyMigrated: Boolean = false,

    val createdAt: Long = System.currentTimeMillis()
) {
    // Computed property: average rating
    val averageRating: Double
        get() = if (ratingCount > 0) {
            kotlin.math.round(ratingSum / ratingCount * 10) / 10.0 // Round to 1 decimal
        } else {
            0.0
        }
    
    // 💎 Helpers for Subscription (Computed, not stored)
    // 💎 isGold = tiene acceso ilimitado (GOLD pago, FOUNDER, o legacy)
    val isGold: Boolean
        get() = isGoldStored || subscriptionType == "GOLD" || subscriptionType == "FOUNDER" || isFounder

    // isPremium = alias de isGold (ya no existe tier separado)
    val isPremium: Boolean
        get() = isGold

    // 🚀 canBoostFree = solo suscriptores GOLD pagos (NO founders)
    val canBoostFree: Boolean
        get() = subscriptionType == "GOLD" && !isFounder

    // Helper para detectar Asistente Perfecto (Punto 5 sugerido)
    fun isPerfectAttendee(): Boolean {
        // Tolerancia 0: debe asistir a todo lo que pide, y tener historial (>5)
        return eventsRequestedCount > 5 && eventsAttendedCount >= eventsRequestedCount
    }

    // Helper para obtener badge según score
    fun getRatingBadge(): String {
        return when {
            isPerfectAttendee() -> "💎" // Perfect Attendee
            ratingCount == 0 -> "🆕" // Nuevo Usuario
            averageRating >= 4.5 -> "🏆" // Top Banana
            averageRating >= 4.0 -> "🥇" // Confiable
            averageRating >= 3.0 -> "✅" // Bueno
            else -> "⚠️" // En Desarrollo
        }
    }
    

}
// Enum removed to simplify Firestore mapping

