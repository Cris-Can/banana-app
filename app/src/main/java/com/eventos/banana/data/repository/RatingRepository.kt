package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.Rating
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class RatingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Guarda una calificación y actualiza el promedio del usuario destino mediante una transacción.
     */
    suspend fun submitRating(rating: Rating): Result<Unit> {
        return try {
            val ratingRef = db.collection("ratings").document()
            val userRef = db.collection("users").document(rating.toUserId)

            db.runTransaction { transaction ->
                // 1. Guardar el documento de rating
                val ratingWithId = rating.copy(id = ratingRef.id)
                transaction.set(ratingRef, ratingWithId)

                // 2. Leer el usuario destino
                val snapshot = transaction.get(userRef)
                val currentSum = snapshot.getDouble("ratingSum") ?: 0.0
                val currentCount = snapshot.getLong("ratingCount") ?: 0

                // 3. Calcular nuevos valores
                val newSum = currentSum + rating.score
                val newCount = currentCount + 1
                val newAverage = newSum / newCount
                val newScoreInt = kotlin.math.round(newAverage).toInt()

                // 4. Actualizar usuario
                transaction.update(
                    userRef,
                    mapOf(
                        "ratingSum" to newSum,
                        "ratingCount" to newCount,
                        "score" to newScoreInt
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hasUserRated(eventId: String, fromUserId: String, toUserId: String): Boolean {
        return try {
            val snapshot = db.collection("ratings")
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("fromUserId", fromUserId)
                .whereEqualTo("toUserId", toUserId)
                .limit(1)
                .get()
                .await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            false
        }
    }
}
