package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.JoinRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class EventRepository {

    private val eventsCollection =
        FirebaseFirestore.getInstance().collection("events")

    // ================= CREAR EVENTO =================
    suspend fun createEvent(event: Event): Result<Unit> {
        return try {
            val docRef = eventsCollection.document()

            val eventWithId = event.copy(
                id = docRef.id,
                createdAt = System.currentTimeMillis(),
                approvedParticipants = listOf(event.creatorId),
                pendingRequests = emptyList(),
                rejectedParticipants = emptyList()
            )

            docRef.set(eventWithId).await()
            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================= OBTENER EVENTO =================
    suspend fun getEventById(eventId: String): Result<Event> {
        return try {
            val snapshot = eventsCollection.document(eventId).get().await()
            val event = snapshot.toObject(Event::class.java)
                ?: return Result.failure(Exception("Evento no encontrado"))

            Result.success(event)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================= SOLICITAR ACCESO =================
    suspend fun requestJoinEventWithAnswers(
        eventId: String,
        userId: String,
        answers: Map<String, String>
    ): Result<Unit> {
        return try {
            FirebaseFirestore.getInstance().runTransaction { tx ->

                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                if (event.creatorId == userId) throw Exception("El creador no solicita acceso")
                if (event.approvedParticipants.contains(userId)) throw Exception("Ya aceptado")
                if (event.rejectedParticipants.contains(userId)) throw Exception("Solicitud rechazada")
                if (event.pendingRequests.any { it.userId == userId }) throw Exception("Solicitud duplicada")

                val request = JoinRequest(
                    userId = userId,
                    answers = answers,
                    createdAt = System.currentTimeMillis()
                )

                tx.update(
                    ref,
                    "pendingRequests",
                    event.pendingRequests + request
                )
            }.await()

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================= APROBAR =================
    suspend fun approveParticipant(eventId: String, userId: String): Result<Unit> {
        return try {
            FirebaseFirestore.getInstance().runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                tx.update(
                    ref,
                    mapOf(
                        "pendingRequests" to event.pendingRequests.filterNot { it.userId == userId },
                        "approvedParticipants" to event.approvedParticipants + userId
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================= RECHAZAR =================
    suspend fun rejectParticipant(eventId: String, userId: String): Result<Unit> {
        return try {
            FirebaseFirestore.getInstance().runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                tx.update(
                    ref,
                    mapOf(
                        "pendingRequests" to event.pendingRequests.filterNot { it.userId == userId },
                        "rejectedParticipants" to event.rejectedParticipants + userId
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
// LISTAR EVENTOS (Home)
// =========================================================
    suspend fun getEvents(): Result<List<Event>> {
        return try {
            val snapshot = eventsCollection
                .orderBy("createdAt")
                .get()
                .await()

            Result.success(snapshot.toObjects(Event::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
