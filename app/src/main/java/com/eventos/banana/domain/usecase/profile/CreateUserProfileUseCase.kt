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
            // Ahora solo guardamos el perfil. 
            // Las Cloud Functions manejarán:
            // 1. El incremento de config/stats/userCount
            // 2. La asignación de estatus FOUNDER si el invitationCode es válido.
            
            firestore.collection("users")
                .document(profile.uid)
                .set(profile)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("CreateUserProfileUC", "Error creando perfil", e)
            Result.failure(e)
        }
    }
}
