package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.Event
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class EventRepository {

    private val eventsCollection =
        FirebaseFirestore.getInstance().collection("events")

    // ---------------- CREAR EVENTO ----------------
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

    // ---------------- LISTAR EVENTOS ----------------
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

    // ---------------- EVENTO POR ID ----------------
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

    // ---------------- SOLICITAR ACCESO ----------------
    suspend fun requestJoinEvent(
        eventId: String,
        userId: String
    ): Result<Unit> {
        return try {
            FirebaseFirestore.getInstance().runTransaction { transaction ->

                val ref = eventsCollection.document(eventId)
                val event = transaction.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                if (event.creatorId == userId) return@runTransaction
                if (event.approvedParticipants.contains(userId)) return@runTransaction
                if (event.pendingRequests.contains(userId)) return@runTransaction
                if (event.rejectedParticipants.contains(userId)) return@runTransaction

                transaction.update(
                    ref,
                    "pendingRequests",
                    event.pendingRequests + userId
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- APROBAR ----------------
    suspend fun approveParticipant(
        eventId: String,
        userId: String
    ): Result<Unit> {
        return try {
            FirebaseFirestore.getInstance().runTransaction { transaction ->

                val ref = eventsCollection.document(eventId)
                val event = transaction.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                transaction.update(
                    ref,
                    mapOf(
                        "pendingRequests" to event.pendingRequests - userId,
                        "approvedParticipants" to event.approvedParticipants + userId
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- RECHAZAR ----------------
    suspend fun rejectParticipant(
        eventId: String,
        userId: String
    ): Result<Unit> {
        return try {
            FirebaseFirestore.getInstance().runTransaction { transaction ->

                val ref = eventsCollection.document(eventId)
                val event = transaction.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                transaction.update(
                    ref,
                    mapOf(
                        "pendingRequests" to event.pendingRequests - userId,
                        "rejectedParticipants" to event.rejectedParticipants + userId
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
