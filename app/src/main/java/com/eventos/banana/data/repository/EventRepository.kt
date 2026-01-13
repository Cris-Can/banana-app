package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.JoinRequest
import com.eventos.banana.domain.model.AppNotification
import com.eventos.banana.domain.model.NotificationType
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class EventRepository {

    private val eventsCollection =
        FirebaseFirestore.getInstance().collection("events")

    private val notificationRepository = NotificationRepository()

    // =========================================================
    // CREAR EVENTO
    // =========================================================
    suspend fun createEvent(event: Event): Result<Unit> {
        return try {
            val docRef = eventsCollection.document()

            val eventWithId = event.copy(
                id = docRef.id,
                createdAt = System.currentTimeMillis(),

                // El creador queda aprobado automáticamente
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

    // =========================================================
    // LISTAR EVENTOS (HOME)
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

    // =========================================================
    // OBTENER EVENTO POR ID
    // =========================================================
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

    // =========================================================
    // SOLICITAR ACCESO CON RESPUESTAS (A7.7)
    // =========================================================
    suspend fun requestJoinEventWithAnswers(
        eventId: String,
        userId: String,
        answers: Map<String, String>
    ): Result<Unit> {
        return try {

            var creatorId = ""
            var eventTitle = ""

            FirebaseFirestore.getInstance().runTransaction { tx ->

                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                creatorId = event.creatorId
                eventTitle = event.title

                if (event.creatorId == userId) {
                    throw Exception("El creador no puede solicitar acceso")
                }

                if (event.approvedParticipants.contains(userId)) {
                    throw Exception("Ya estás aceptado")
                }

                if (event.rejectedParticipants.contains(userId)) {
                    throw Exception("Solicitud rechazada previamente")
                }

                if (event.pendingRequests.any { it.userId == userId }) {
                    throw Exception("Solicitud duplicada")
                }

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

            // 🔔 Notificación al creador
            notificationRepository.sendNotification(
                AppNotification(
                    userId = creatorId,
                    title = "Nueva solicitud",
                    message = "Un usuario solicitó unirse a tu evento \"$eventTitle\"",
                    eventId = eventId,
                    createdAt = System.currentTimeMillis(),
                    type = NotificationType.JOIN_REQUEST_SENT
                )
            )

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    // APROBAR PARTICIPANTE (CREADOR)
    // =========================================================
    suspend fun approveParticipant(
        eventId: String,
        userId: String
    ): Result<Unit> {
        return try {

            var eventTitle = ""

            FirebaseFirestore.getInstance().runTransaction { tx ->

                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                eventTitle = event.title

                if (event.approvedParticipants.size >= event.maxParticipants) {
                    throw Exception("Evento sin cupos")
                }

                tx.update(
                    ref,
                    mapOf(
                        "pendingRequests" to event.pendingRequests
                            .filterNot { it.userId == userId },
                        "approvedParticipants" to
                                (event.approvedParticipants + userId)
                    )
                )
            }.await()

            // 🔔 Notificación al usuario
            notificationRepository.sendNotification(
                AppNotification(
                    userId = userId,
                    title = "Solicitud aceptada",
                    message = "Fuiste aceptado en el evento \"$eventTitle\"",
                    eventId = eventId,
                    createdAt = System.currentTimeMillis(),
                    type = NotificationType.JOIN_APPROVED
                )
            )

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    // RECHAZAR PARTICIPANTE (CREADOR)
    // =========================================================
    suspend fun rejectParticipant(
        eventId: String,
        userId: String
    ): Result<Unit> {
        return try {

            var eventTitle = ""

            FirebaseFirestore.getInstance().runTransaction { tx ->

                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                eventTitle = event.title

                tx.update(
                    ref,
                    mapOf(
                        "pendingRequests" to event.pendingRequests
                            .filterNot { it.userId == userId },
                        "rejectedParticipants" to
                                (event.rejectedParticipants + userId)
                    )
                )
            }.await()

            // 🔔 Notificación al usuario
            notificationRepository.sendNotification(
                AppNotification(
                    userId = userId,
                    title = "Solicitud rechazada",
                    message = "Tu solicitud para el evento \"$eventTitle\" fue rechazada",
                    eventId = eventId,
                    createdAt = System.currentTimeMillis(),
                    type = NotificationType.JOIN_REJECTED
                )
            )

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeEvents(): Flow<List<Event>> = callbackFlow {

        val listener = eventsCollection
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val events = snapshot
                    ?.toObjects(Event::class.java)
                    ?: emptyList()

                trySend(events)
            }

        awaitClose {
            listener.remove()
        }
    }
}
