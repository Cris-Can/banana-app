package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.UserRating
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log

import javax.inject.Inject

class RatingRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val encounterRepo: EncounterRepository
) {
    companion object {
        private const val TAG = "RatingRepository"
        private const val COLLECTION_RATINGS = "ratings"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_SKIPS = "rating_skips" // Nueva colección de saltos
        private const val EDIT_WINDOW_MS = 10 * 60 * 1000L // 10 minutos
        private const val RATING_DEADLINE_DAYS = 5L
    }

    /**
     * Enviar una nueva puntuación
     */
    suspend fun submitRating(
        eventId: String,
        eventType: com.eventos.banana.domain.model.EventType,
        fromUserId: String,
        toUserId: String,
        score: Double,
        comment: String?
    ): Result<String> {
        return try {
            // Verificar que no se esté puntuando a sí mismo
            if (fromUserId == toUserId) {
                return Result.failure(Exception("No puedes puntuarte a ti mismo"))
            }

            // Verificar que no exista ya una puntuación de este usuario en este evento
            val existing = db.collection(COLLECTION_RATINGS)
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("fromUserId", fromUserId)
                .whereEqualTo("toUserId", toUserId)
                .get()
                .await()

            if (!existing.isEmpty) {
                return Result.failure(Exception("Ya puntuaste a este usuario en este evento"))
            }

            val now = System.currentTimeMillis()
            val ratingId = db.collection(COLLECTION_RATINGS).document().id

            val rating = UserRating(
                ratingId = ratingId,
                eventId = eventId,
                eventType = eventType,
                fromUserId = fromUserId,
                toUserId = toUserId,
                score = score,
                comment = comment,
                timestamp = now,
                canEditUntil = now + EDIT_WINDOW_MS,
                isEdited = false
            )

            db.collection(COLLECTION_RATINGS)
                .document(ratingId)
                .set(rating.toMap())
                .await()

            // 🔥 Actualizar el score manualmente (E3 Fix)
            updateUserScore(toUserId)


            Log.d(TAG, "Rating submitted: $fromUserId -> $toUserId = $score")
            Result.success(ratingId)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting rating", e)
            Result.failure(e)
        }
    }

    suspend fun getAlreadyRatedUsers(eventId: String, fromUserId: String): Result<Set<String>> {
        return try {
            val existingRatings = db.collection(COLLECTION_RATINGS)
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("fromUserId", fromUserId)
                .get()
                .await()

            val alreadyRated = existingRatings.documents.mapNotNull { doc ->
                doc.getString("toUserId")?.trim()
            }.toSet()

            Log.d(TAG, "Direct fetch already rated: $alreadyRated for event $eventId")
            Result.success(alreadyRated)
        } catch (e: Exception) {
            Log.e(TAG, "Error directly fetching already rated users", e)
            Result.failure(e)
        }
    }

    /**
     * Editar una puntuación (solo si está dentro de los 10 minutos)
     */
    suspend fun editRating(
        ratingId: String,
        newScore: Double,
        newComment: String?
    ): Result<Unit> {
        return try {
            val doc = db.collection(COLLECTION_RATINGS)
                .document(ratingId)
                .get()
                .await()

            if (!doc.exists()) {
                return Result.failure(Exception("Puntuación no encontrada"))
            }

            val rating = UserRating.fromMap(doc.data ?: emptyMap())

            // Verificar si aún puede editar
            if (!rating.canEdit()) {
                return Result.failure(Exception("Ya pasaron los 10 minutos, no puedes editar"))
            }

            // Actualizar
            db.collection(COLLECTION_RATINGS)
                .document(ratingId)
                .update(
                    mapOf(
                        "score" to newScore,
                        "comment" to newComment,
                        "isEdited" to true
                    )
                )
                .await()

            // Recalcular score del usuario
            updateUserScore(rating.toUserId)

            Log.d(TAG, "Rating edited: $ratingId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error editing rating", e)
            Result.failure(e)
        }
    }

    suspend fun getUserRatings(
        userId: String,
        isPremium: Boolean,
        limit: Int = 20,
        startAfter: com.google.firebase.firestore.DocumentSnapshot? = null
    ): Result<Pair<List<UserRating>, com.google.firebase.firestore.DocumentSnapshot?>> {
        return try {
            var query = db.collection(COLLECTION_RATINGS)
                .whereEqualTo("toUserId", userId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())

            startAfter?.let {
                query = query.startAfter(it)
            }

            val snapshot = query.get().await()

            val lastVisible = if (snapshot.documents.isNotEmpty()) {
                snapshot.documents[snapshot.size() - 1]
            } else null

            val ratings = snapshot.documents.mapNotNull { doc ->
                UserRating.fromMap(doc.data ?: emptyMap()).let { rating ->
                    // Si no es premium, ocultar quién calificó (fromUserId)
                    // Los comentarios ahora son visibles para todos según requerimiento
                    if (!isPremium) {
                        rating.copy(fromUserId = "anonymous")
                    } else {
                        rating
                    }
                }
            }

            Log.d(TAG, "Fetched ${ratings.size} ratings for user $userId")
            Result.success(Pair(ratings, lastVisible))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user ratings", e)
            Result.failure(e)
        }
    }

    /**
     * Obtener puntuaciones de un evento específico
     */
    suspend fun getEventRatings(eventId: String): Result<List<UserRating>> {
        return try {
            val snapshot = db.collection(COLLECTION_RATINGS)
                .whereEqualTo("eventId", eventId)
                .get()
                .await()

            val ratings = snapshot.documents.mapNotNull { doc ->
                UserRating.fromMap(doc.data ?: emptyMap())
            }

            Log.d(TAG, "Fetched ${ratings.size} ratings for event $eventId")
            Result.success(ratings)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching event ratings", e)
            Result.failure(e)
        }
    }

    /**
     * Verificar si un usuario puede puntuar en un evento
     */
    suspend fun canUserRate(
        userId: String,
        eventId: String,
        targetUserId: String
    ): Result<Boolean> {
        return try {
            // Verificar que no se esté puntuando a sí mismo
            if (userId == targetUserId) {
                return Result.success(false)
            }

            // Verificar que no haya puntuado ya
            val existing = db.collection(COLLECTION_RATINGS)
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("fromUserId", userId)
                .whereEqualTo("toUserId", targetUserId)
                .get()
                .await()

            if (!existing.isEmpty) {
                return Result.success(false)
            }

            // NUEVO (Round 12): Verificar encuentro físico si el evento lo requiere
            val shouldEnforce = encounterRepo.shouldEnforceEncounters(eventId).getOrDefault(false)
            
            if (shouldEnforce) {
                val hasEncounter = encounterRepo.hasEncounter(eventId, userId, targetUserId)
                    .getOrDefault(false)
                
                if (!hasEncounter) {
                    Log.d(TAG, "Cannot rate: no encounter confirmed with $targetUserId")
                    return Result.success(false)
                }
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if user can rate", e)
            Result.failure(e)
        }
    }

    /**
     * Actualizar el score promedio de un usuario
     */
    suspend fun updateUserScore(userId: String): Result<Unit> {
        return try {
            // Obtener todas las puntuaciones del usuario
            val snapshot = db.collection(COLLECTION_RATINGS)
                .whereEqualTo("toUserId", userId)
                .get()
                .await()

            val ratings = snapshot.documents.mapNotNull { doc ->
                UserRating.fromMap(doc.data ?: emptyMap())
            }

            if (ratings.isEmpty()) {
                // Sin puntuaciones, resetear
                db.collection(COLLECTION_USERS)
                    .document(userId)
                    .update(
                        mapOf(
                            "ratingSum" to 0.0,
                            "ratingCount" to 0
                        )
                    )
                    .await()
            } else {
                val sum = ratings.sumOf { it.score }
                val count = ratings.size

                db.collection(COLLECTION_USERS)
                    .document(userId)
                    .update(
                        mapOf(
                            "ratingSum" to sum,
                            "ratingCount" to count
                        )
                    )
                    .await()

                Log.d(TAG, "Updated score for $userId: $sum / $count = ${sum / count}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user score", e)
            Result.failure(e)
        }
    }

    /**
     * Marcar un evento como puntuable y calcular deadline
     */
    suspend fun markEventAsRatable(eventId: String, eventDate: Long): Result<Unit> {
        return try {
            val ratingDeadline = eventDate + (RATING_DEADLINE_DAYS * 24 * 60 * 60 * 1000)

            db.collection("events")
                .document(eventId)
                .update(
                    mapOf(
                        "canBeRated" to true,
                        "ratingDeadline" to ratingDeadline
                    )
                )
                .await()

            Log.d(TAG, "Event $eventId marked as ratable until $ratingDeadline")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking event as ratable", e)
            Result.failure(e)
        }
    }

    /**
     * Obtener usuarios a puntuar en un evento (excluyendo al propio usuario)
     */
    suspend fun getUsersToRate(
        eventId: String,
        currentUserId: String,
        approvedParticipants: List<String>,
        creatorId: String
    ): Result<List<String>> {
        return try {
            // Lista de usuarios a puntuar: participantes + creador, excluyendo propio userId
            val allUsers = (approvedParticipants + creatorId).toSet()
            val usersToRate = allUsers.filter { it != currentUserId }

            // Obtener las puntuaciones ya realizadas por este usuario
            val existingRatings = db.collection(COLLECTION_RATINGS)
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("fromUserId", currentUserId)
                .get()
                .await()

            val alreadyRated = existingRatings.documents.mapNotNull { doc ->
                (doc.data?.get("toUserId") as? String)
            }.toSet()

            // Verificar si el usuario ha decidido PASAR de calificar este evento
            val skipResult = db.collection(COLLECTION_SKIPS)
                .document("${currentUserId}_$eventId")
                .get()
                .await()
            
            if (skipResult.exists()) {
                Log.d(TAG, "User $currentUserId skipped rating for event $eventId")
                return Result.success(emptyList()) // Nada pendiente si saltó
            }

            // Filtrar los ya puntuados
            val pending = usersToRate.filter { it !in alreadyRated }

            Log.d(TAG, "Users to rate in event $eventId: ${pending.size}")
            Result.success(pending)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting users to rate", e)
            Result.failure(e)
        }
    }
    
    /**
     * LEGACY: Check if user already rated (backward compatibility)
     */
    suspend fun hasUserRated(eventId: String, fromUserId: String, toUserId: String): Boolean {
        return try {
            val result = canUserRate(fromUserId, eventId, toUserId).getOrNull()
            result == false // If can't rate, means already rated
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Omitir las calificaciones pendientes de un evento
     */
    suspend fun skipRating(userId: String, eventId: String): Result<Unit> {
        return try {
            db.collection(COLLECTION_SKIPS)
                .document("${userId}_$eventId")
                .set(mapOf(
                    "userId" to userId,
                    "eventId" to eventId,
                    "timestamp" to System.currentTimeMillis()
                ))
                .await()
            
            Log.d(TAG, "User $userId skipped event $eventId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error skipping rating", e)
            Result.failure(e)
        }
    }

    /**
     * Comprobar si un usuario omitió las calificaciones pendientes de un evento
     */
    suspend fun hasSkippedRating(userId: String, eventId: String): Result<Boolean> {
        return try {
            val skipResult = db.collection(COLLECTION_SKIPS)
                .document("${userId}_$eventId")
                .get()
                .await()
            Result.success(skipResult.exists())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking skipped rating", e)
            Result.failure(e)
        }
    }
}
