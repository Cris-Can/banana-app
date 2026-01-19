package com.eventos.banana.domain.model

import com.google.firebase.firestore.PropertyName

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val nickname: String = "",
    val region: String? = null,
    val commune: String? = null,
    val notifyEventsByCommune: Boolean = false,
    val fcmToken: String? = null,
    val appTheme: String = "BANANA", // 🎨 Options: BANANA, DARK, LIGHT
    @get:PropertyName("isVerified") @set:PropertyName("isVerified") var isVerified: Boolean = false, // 👈 A23 Account verification

    val score: Int = 0,
    
    // ⭐ PROMEDIO PUNTUACIÓN
    val ratingSum: Double = 0.0,
    val ratingCount: Int = 0,
    
    // 🎨 SOCIAL (A20)
    val aboutMe: String = "",
    val interests: List<String> = emptyList(),
    val profilePictureUrl: String? = null, // Foto de perfil principal
    val photos: List<String> = emptyList(), // URLs de fotos (galería)

    // 🤝 AMIGOS (A20)
    val friends: List<String> = emptyList(),              // UIDs de amigos confirmados
    val friendRequestsReceived: List<String> = emptyList(), // UIDs de solicitudes entrantes
    val friendRequestsSent: List<String> = emptyList(),     // UIDs de solicitudes enviadas
    
    val createdAt: Long = System.currentTimeMillis()
)
