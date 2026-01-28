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
    private val checkinsCollection = db.collection("event_checkins")
    private val userRepository = UserRepository() // 🆕

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
            
            // 📊 STATS: Increment attendance for both users IF it's their first interaction/check-in for this event
            // Note: Optimally we check if they already 'attended' this event via check-in.
            // For simplicity in this task, we rely on recordCheckIn primarily for the stat, 
            // OR we could check here. But let's stick to recordCheckIn for the "Official Attendance".
            // Actually, let's auto-record check-in for both? 
            // User requested "cuantos asiste de verdad". Interacting is attending.
            // Let's call recordCheckIn() for both users here safely.
            recordCheckIn(eventId, userId1)
            recordCheckIn(eventId, userId2)
            
            Log.d(TAG, "Encounter recorded: $userId1 <-> $userId2 via $method")
            Result.success(encounterId)
        } catch (e: Exception) {
            Log.e(TAG, "Error recording encounter", e)
            Result.failure(e)
        }
    }

    // =========================================================
    // GPS CHECK-INS (Individual)
    // =========================================================

    /**
     * Registrar que un usuario estuvo en el evento (Check-in GPS individual)
     */
    suspend fun recordCheckIn(
        eventId: String,
        userId: String
    ): Result<Unit> {
        return try {
            val checkInId = "${eventId}_${userId}"
            val docRef = checkinsCollection.document(checkInId)
            
            // 1. Check if exists
            val snapshot = docRef.get().await()
            val isNewCheckIn = !snapshot.exists()
            
            val data = hashMapOf(
                "eventId" to eventId,
                "userId" to userId,
                "timestamp" to System.currentTimeMillis()
            )
            
            docRef.set(data).await()
            
            // 2. Increment stats if new
            if (isNewCheckIn) {
                userRepository.incrementEventsAttended(userId)
                Log.d(TAG, "Incremented attendance stat for $userId")
            }
            
            Log.d(TAG, "GPS Check-in recorded for user $userId at event $eventId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error recording GPS check-in", e)
            Result.failure(e)
        }
    }

    /**
     * Verificar si un usuario hizo check-in en el evento
     */
    suspend fun hasCheckIn(eventId: String, userId: String): Boolean {
        return try {
            val checkInId = "${eventId}_${userId}"
            val doc = checkinsCollection.document(checkInId).get().await()
            doc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking check-in", e)
            false
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
            // 1. Verificar encuentro directo (NFC)
            val encounterId = Encounter.createId(eventId, userId1, userId2)
            val doc = encountersCollection.document(encounterId).get().await()
            
            if (doc.exists()) return Result.success(true)

            // 2. Verificar si AMBOS hicieron check-in GPS (Fallback)
            val user1CheckedIn = hasCheckIn(eventId, userId1)
            val user2CheckedIn = hasCheckIn(eventId, userId2)

            if (user1CheckedIn && user2CheckedIn) {
                 Log.d(TAG, "Encounter validated via Dual GPS Check-in: $userId1 & $userId2")
                 return Result.success(true)
            }

            Result.success(false)
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
            val metUserIds = mutableSetOf<String>()

            // 1. Buscar encuentros directos (NFC)
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
            
            asUser1.documents.forEach { doc ->
                val encounter = Encounter.fromMap(doc.data ?: emptyMap())
                metUserIds.add(encounter.userId2)
            }
            
            asUser2.documents.forEach { doc ->
                val encounter = Encounter.fromMap(doc.data ?: emptyMap())
                metUserIds.add(encounter.userId1)
            }

            // 2. Si el usuario hizo Check-in GPS, agregar a todos los demás que también hicieron Check-in
            if (hasCheckIn(eventId, userId)) {
                val allCheckins = checkinsCollection
                    .whereEqualTo("eventId", eventId)
                    .get()
                    .await()
                
                allCheckins.documents.forEach { doc ->
                    val otherUserId = doc.getString("userId")
                    if (otherUserId != null && otherUserId != userId) {
                        metUserIds.add(otherUserId)
                    }
                }
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
     * Verificar si el usuario asistió realmente (Check-in GPS, NFC o Creador)
     * Usado para permitir calificar.
     */
    suspend fun hasAttended(eventId: String, userId: String, isCreator: Boolean): Boolean {
        if (isCreator) return true
        return hasCheckIn(eventId, userId)
    }

    /**
     * Por defecto, si no hay encuentros registrados, permitir puntuar a todos
     * (fallback para eventos antiguos o sin NFC habilitado)
     */
    suspend fun shouldEnforceEncounters(eventId: String): Result<Boolean> {
        return try {
             // Verificar si hay AL MENOS UN check-in GPS o un encuentro NFC
             // Si nadie ha usado el sistema, asumimos evento legacy -> return false
             
             val hasEncounters = !encountersCollection
                .whereEqualTo("eventId", eventId)
                .limit(1)
                .get()
                .await()
                .isEmpty

             if (hasEncounters) return Result.success(true)

             val hasCheckins = !checkinsCollection
                .whereEqualTo("eventId", eventId)
                .limit(1)
                .get()
                .await()
                .isEmpty
            
            Result.success(hasCheckins)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking enforcement", e)
            Result.success(false) // Por defecto, no enforceamos en caso de error
        }
    }
}
