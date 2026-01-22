package com.eventos.banana.data.repository

import android.util.Log
import com.eventos.banana.domain.model.Encounter
import com.eventos.banana.domain.model.EncounterMethod
import com.eventos.banana.domain.model.EncounterLocation
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository para gestionar "encuentros" (confirmación de que dos usuarios se conocieron físicamente)
 * Usado para validar que solo se puedan puntuar personas con quien realmente interactuaste.
 */
class EncounterRepository {

    private val db = FirebaseFirestore.getInstance()
    private val encountersCollection = db.collection("encounters")

    companion object {
        private const val TAG = "EncounterRepository"
    }

    // =========================================================
    // REGISTRAR ENCUENTROS
    // =========================================================

    /**
     * Registrar un encuentro entre dos usuarios (NFC, GPS, etc.)
     */
    suspend fun recordEncounter(
        eventId: String,
        userId1: String,
        userId2: String,
        method: EncounterMethod,
        location: EncounterLocation? = null
    ): Result<String> {
        return try {
            // Prevenir auto-encuentro
            if (userId1 == userId2) {
                return Result.failure(Exception("No puedes registrar encuentro contigo mismo"))
            }

            // Crear ID único (ordenado alfabéticamente para evitar duplicados)
            val encounterId = Encounter.createId(eventId, userId1, userId2)
            
            // Verificar si ya existe
            val existing = encountersCollection.document(encounterId).get().await()
            if (existing.exists()) {
                Log.d(TAG, "Encounter already exists: $encounterId")
                return Result.success(encounterId)
            }

            val encounter = Encounter(
                encounterId = encounterId,
                eventId = eventId,
                userId1 = if (userId1 < userId2) userId1 else userId2,
                userId2 = if (userId1 < userId2) userId2 else userId1,
                method = method,
                timestamp = System.currentTimeMillis(),
                location = location,
                confirmedByCreator = false
            )

            encountersCollection.document(encounterId).set(encounter.toMap()).await()
            
            Log.d(TAG, "Encounter recorded: $userId1 <-> $userId2 via $method")
            Result.success(encounterId)
        } catch (e: Exception) {
            Log.e(TAG, "Error recording encounter", e)
            Result.failure(e)
        }
    }

    // =========================================================
    // CONSULTAR ENCUENTROS
    // =========================================================

    /**
     * Verificar si dos usuarios tuvieron un encuentro en un evento específico
     */
    suspend fun hasEncounter(
        eventId: String,
        userId1: String,
        userId2: String
    ): Result<Boolean> {
        return try {
            val encounterId = Encounter.createId(eventId, userId1, userId2)
            val doc = encountersCollection.document(encounterId).get().await()
            Result.success(doc.exists())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking encounter", e)
            Result.failure(e)
        }
    }

    /**
     * Obtener todos los usuarios con quien un usuario tuvo encuentros en un evento
     */
    suspend fun getEncountersForUser(
        eventId: String,
        userId: String
    ): Result<List<String>> {
        return try {
            // Buscar donde es userId1
            val asUser1 = encountersCollection
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("userId1", userId)
                .get()
                .await()

            // Buscar donde es userId2
            val asUser2 = encountersCollection
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("userId2", userId)
                .get()
                .await()

            val metUserIds = mutableSetOf<String>()
            
            asUser1.documents.forEach { doc ->
                val encounter = Encounter.fromMap(doc.data ?: emptyMap())
                metUserIds.add(encounter.userId2)
            }
            
            asUser2.documents.forEach { doc ->
                val encounter = Encounter.fromMap(doc.data ?: emptyMap())
                metUserIds.add(encounter.userId1)
            }

            Log.d(TAG, "User $userId met ${metUserIds.size} people in event $eventId")
            Result.success(metUserIds.toList())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting encounters", e)
            Result.failure(e)
        }
    }

    // =========================================================
    // CREADOR: OVERRIDE MANUAL
    // =========================================================

    /**
     * Permitir al creador del evento confirmar manualmente que dos personas se conocieron
     * (útil si hubo problemas técnicos con NFC/GPS)
     */
    suspend fun manualConfirmEncounter(
        eventId: String,
        userId1: String,
        userId2: String,
        creatorId: String
    ): Result<String> {
        return try {
            // TODO: Verificar que quien confirma es el creador del evento
            
            val result = recordEncounter(
                eventId = eventId,
                userId1 = userId1,
                userId2 = userId2,
                method = EncounterMethod.MANUAL_OVERRIDE
            )

            if (result.isSuccess) {
                // Marcar como confirmado por creador
                val encounterId = result.getOrNull() ?: return result
                encountersCollection.document(encounterId)
                    .update("confirmedByCreator", true)
                    .await()
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in manual confirm", e)
            Result.failure(e)
        }
    }

    // =========================================================
    // LEGACY COMPATIBILITY
    // =========================================================

    /**
     * Por defecto, si no hay encuentros registrados, permitir puntuar a todos
     * (fallback para eventos antiguos o sin NFC habilitado)
     */
    suspend fun shouldEnforceEncounters(eventId: String): Result<Boolean> {
        return try {
            val encounters = encountersCollection
                .whereEqualTo("eventId", eventId)
                .limit(1)
                .get()
                .await()

            // Si hay al menos 1 encuentro registrado, enforceamos la regla
            Result.success(encounters.documents.isNotEmpty())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking enforcement", e)
            Result.success(false) // Por defecto, no enforceamos en caso de error
        }
    }
}
