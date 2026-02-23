package com.eventos.banana.domain.usecase.event

import com.eventos.banana.data.repository.EncounterRepository
import com.eventos.banana.data.repository.UserRepository
import javax.inject.Inject

/**
 * UseCase para registrar la asistencia (Check-in) de un usuario a un evento.
 * Orquesta el guardado del check-in y el incremento de estadísticas de asistencia.
 */
class ProcessCheckInUseCase @Inject constructor(
    private val encounterRepository: EncounterRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(eventId: String, userId: String): Result<Unit> {
        return try {
            // 1. Verificar si ya existe el check-in
            val hasCheckIn = encounterRepository.hasCheckIn(eventId, userId)
            if (hasCheckIn) {
                return Result.success(Unit)
            }

            // 2. Registrar Check-in en el repositorio
            val result = encounterRepository.recordCheckIn(eventId, userId)
            
            if (result.isSuccess) {
                // 3. Incrementar estadísticas de asistencia en el perfil del usuario
                userRepository.incrementEventsAttended(userId)
                Result.success(Unit)
            } else {
                result
            }
        } catch (e: Exception) {
            android.util.Log.e("ProcessCheckInUseCase", "Error processing check-in", e)
            Result.failure(e)
        }
    }
}
