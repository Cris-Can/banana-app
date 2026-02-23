package com.eventos.banana.domain.usecase.event

import com.eventos.banana.data.repository.EncounterRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.Encounter
import com.eventos.banana.domain.model.EncounterMethod
import com.eventos.banana.domain.model.EncounterLocation
import javax.inject.Inject

/**
 * UseCase para registrar un encuentro físico entre dos usuarios.
 * Orquesta el guardado del encuentro y la actualización de estadísticas de asistencia.
 */
class ProcessEncounterUseCase @Inject constructor(
    private val encounterRepository: EncounterRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        eventId: String,
        userId1: String,
        userId2: String,
        method: EncounterMethod,
        location: EncounterLocation? = null
    ): Result<String> {
        return try {
            if (userId1 == userId2) {
                return Result.failure(Exception("No puedes registrar encuentro contigo mismo"))
            }

            // 1. Registrar Encuentro Físico
            val result = encounterRepository.recordEncounter(eventId, userId1, userId2, method, location)
            
            if (result.isSuccess) {
                val encounterId = result.getOrThrow()
                
                // 2. Registrar Asistencia (Check-in) para ambos usuarios si no existe
                // Esto incrementa stats automáticamente en UserRepository a través de recordCheckIn
                recordAttendanceIfNew(eventId, userId1)
                recordAttendanceIfNew(eventId, userId2)
                
                Result.success(encounterId)
            } else {
                result
            }
        } catch (e: Exception) {
            android.util.Log.e("ProcessEncounterUC", "Error processing encounter", e)
            Result.failure(e)
        }
    }

    private suspend fun recordAttendanceIfNew(eventId: String, userId: String) {
        try {
            val hasCheckIn = encounterRepository.hasCheckIn(eventId, userId)
            if (!hasCheckIn) {
                encounterRepository.recordCheckIn(eventId, userId)
                // userRepository.incrementEventsAttended(userId) // Delegado ahora a recordCheckIn
            }
        } catch (e: Exception) {
            android.util.Log.e("ProcessEncounterUC", "Error updating attendance for $userId", e)
        }
    }
}
