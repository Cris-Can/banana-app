package com.eventos.banana.domain.usecase.profile

import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * UseCase para gestionar el registro inicial del usuario.
 * Aplica lógica de negocio como la asignación de estado "Founder".
 */
class CreateUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val firestore: FirebaseFirestore
) {
    suspend operator fun invoke(profile: UserProfile): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val statsRef = firestore.collection("config").document("stats")
                val statsSnapshot = transaction.get(statsRef)
                
                // Obtener conteo actual
                val currentCount = if (statsSnapshot.exists()) {
                    statsSnapshot.getLong("userCount") ?: 0L
                } else {
                    0L
                }
                
                val newCount = currentCount + 1
                
                // Actualizar estadísticas globales
                transaction.set(
                    statsRef, 
                    mapOf("userCount" to newCount), 
                    SetOptions.merge()
                )
                
                // Lógica de Negocio: Primeros 40 usuarios son FOUNDERS
                val finalProfile = if (newCount <= 40) {
                    profile.copy(
                        isGoldStored = true,
                        isPremiumStored = true,
                        subscriptionType = "FOUNDER",
                        isFounder = true
                    )
                } else {
                    profile
                }
                
                // Guardar perfil a través del repositorio (dentro de la transacción)
                // Nota: Inyectamos el repo para mantener consistencia, pero usamos la transacción de Firestore
                val userRef = firestore.collection("users").document(profile.uid)
                transaction.set(userRef, finalProfile)
            }.await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("CreateUserProfileUC", "Error creando perfil", e)
            Result.failure(e)
        }
    }
}
