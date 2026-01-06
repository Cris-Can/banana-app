package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.Event
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class EventRepository {

    private val eventsCollection =
        FirebaseFirestore.getInstance().collection("events")

    suspend fun createEvent(event: Event): Result<Unit> {
        return try {
            println("DEBUG ▶️ Intentando guardar en Firestore")

            val docRef = eventsCollection.document()
            val eventWithId = event.copy(
                id = docRef.id,
                createdAt = System.currentTimeMillis()
            )

            docRef.set(eventWithId).await()

            println("DEBUG ✅ Firestore write OK: ${docRef.id}")
            Result.success(Unit)

        } catch (e: Exception) {
            println("DEBUG ❌ Firestore error: ${e.message}")
            Result.failure(e)
        }
    }

}
