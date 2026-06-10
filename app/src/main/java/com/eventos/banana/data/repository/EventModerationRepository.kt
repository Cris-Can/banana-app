package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.EventStatus
import com.eventos.banana.data.remote.model.EventDto
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class EventModerationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val eventsCollection = firestore.collection("events")

    // =========================================================
    // ARCHIVAR EVENTOS PASADOS (A17 base)
    // =========================================================
    suspend fun archivePastEvents(): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()

            val snapshot = eventsCollection
                .whereLessThan("endAt", now)
                .limit(500)
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.update(doc.reference, mapOf("status" to EventStatus.CLOSED))
            }
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    // AUTO-MARK EVENTS AS RATABLE (Round 11)
    // =========================================================
    /**
     * Marca eventos finalizados como puntuables y establece el deadline de 5 días.
     * Esta función debe llamarse periódicamente (ej: cada vez que se abre la app o en un worker).
     */
    suspend fun markFinishedEventsAsRatable(): Result<Int> {
        return try {
            val now = System.currentTimeMillis()
            
            // Buscar eventos cerrados que ya terminaron pero aún no están marcados como puntuables
            val eventsToMark = eventsCollection
                .whereEqualTo("status", EventStatus.CLOSED.name)
                .whereLessThan("endAt", now)
                .whereEqualTo("canBeRated", false)
                .limit(500)
                .get()
                .await()
            
            var markedCount = 0
            val batch = firestore.batch()
            
            eventsToMark.documents.forEach { doc ->
                val eventDto = doc.toObject(EventDto::class.java)
                if (eventDto != null) {
                    val event = eventDto.toDomain()
                    val ratingDeadline = event.endAt + com.eventos.banana.util.AppConstants.RATING_DEADLINE_MS
                    
                    batch.update(doc.reference, mapOf(
                        "canBeRated" to true,
                        "ratingDeadline" to ratingDeadline
                    ))
                    markedCount++
                }
            }
            
            if (markedCount > 0) {
                batch.commit().await()
                android.util.Log.d("EventModerationRepo", "Marked $markedCount events as ratable")
            }
            
            Result.success(markedCount)
        } catch (e: Exception) {
            android.util.Log.e("EventModerationRepo", "Error marking events as ratable", e)
            Result.failure(e)
        }
    }
}
