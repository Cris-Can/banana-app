package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.eventos.banana.data.remote.model.EventDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

import javax.inject.Inject

class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val notificationRepository: NotificationRepository,
    private val storageDataSource: com.eventos.banana.data.remote.storage.FirebaseStorageDataSource
) {

    private val eventsCollection = firestore.collection("events")

    suspend fun createEvent(event: Event, imageBytes: ByteArray? = null): Result<Unit> {
        val docRef = eventsCollection.document()
        val imagePath = "events_covers/${docRef.id}.jpg"
        
        return try {
            var imageUrl: String? = null
            if (imageBytes != null && imageBytes.isNotEmpty()) {
                val uploadResult = storageDataSource.uploadFile(imagePath, imageBytes)
                imageUrl = uploadResult.getOrThrow()
            }

            val eventWithId = event.copy(
                id = docRef.id,
                imageUrl = imageUrl,
                createdAt = System.currentTimeMillis(),
                approvedParticipants = listOf(event.creatorId),
                pendingRequests = emptyList(),
                rejectedParticipants = emptyList()
            )

            val eventDto = EventDto.fromDomain(eventWithId)
            docRef.set(eventDto).await()
            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // =========================================================
    // ARCHIVAR EVENTOS PASADOS (A17 base)
    // =========================================================
    suspend fun archivePastEvents(): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()

            val snapshot = eventsCollection
                .whereLessThan("endAt", now)
                .get()
                .await()

            snapshot.documents.forEach { doc ->
                doc.reference.update(
                    mapOf(
                        "status" to EventStatus.CLOSED
                    )
                ).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    // DETALLES DE EVENTO (REALTIME)
    // =========================================================

    fun listenToEvent(eventId: String): Flow<Event> = callbackFlow {
        val listener = eventsCollection.document(eventId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("EventRepository", "listenToEvent error: ${error.message}", error)
                    return@addSnapshotListener
                }
                val eventDto = snapshot?.toObject(EventDto::class.java)
                if (eventDto != null) {
                    trySend(eventDto.toDomain())
                }
            }
        awaitClose { listener.remove() }
    }

    // =========================================================
    // OBTENER EVENTO POR ID
    // =========================================================
    suspend fun getEventById(eventId: String): Result<Event> {
        return try {
            val snapshot = eventsCollection.document(eventId).get().await()
            val eventDto = snapshot.toObject(EventDto::class.java)
                ?: return Result.failure(Exception("Evento no encontrado"))

            Result.success(eventDto.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    // CONSULTAS PARA PERFIL DE USUARIO
    // =========================================================
    suspend fun getEventsByCreatorId(creatorId: String, source: com.google.firebase.firestore.Source = com.google.firebase.firestore.Source.DEFAULT): List<Event> {
        return try {
            val snapshot = eventsCollection
                .whereEqualTo("creatorId", creatorId)
                .get(source).await()
            snapshot.toObjects(EventDto::class.java).map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEventsByParticipantId(participantId: String, source: com.google.firebase.firestore.Source = com.google.firebase.firestore.Source.DEFAULT): List<Event> {
        return try {
            val snapshot = eventsCollection
                .whereArrayContains("approvedParticipants", participantId)
                .get(source).await()
            snapshot.toObjects(EventDto::class.java).map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEventsByIds(eventIds: List<String>, source: com.google.firebase.firestore.Source = com.google.firebase.firestore.Source.DEFAULT): List<Event> {
        if (eventIds.isEmpty()) return emptyList()
        return try {
            if (eventIds.size <= 30) {
                val chunks = eventIds.chunked(10)
                val results = mutableListOf<Event>()
                chunks.forEach { chunk ->
                    val snapshot = eventsCollection
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get(source).await()
                    results.addAll(snapshot.toObjects(EventDto::class.java).map { it.toDomain() })
                }
                results
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // =========================================================
    // SOLICITAR ACCESO
    // =========================================================
    suspend fun requestJoinEventWithAnswers(
        eventId: String,
        userId: String,
        answers: Map<String, String>
    ): Result<Unit> {
        return try {

            var creatorId = ""
            var eventTitle = ""

            val transactionSuccess = firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(EventDto::class.java)?.toDomain()
                    ?: return@runTransaction false

                if (event.status != EventStatus.OPEN) {
                    throw Exception("El evento no acepta solicitudes")
                }

                if (event.creatorId == userId) {
                    throw Exception("El creador no puede solicitar acceso")
                }

                if (
                    event.approvedParticipants.contains(userId) ||
                    event.rejectedParticipants.contains(userId) ||
                    (event.pendingRequests as? List<JoinRequest?>)?.filterNotNull()?.any { it.userId == userId } == true
                ) {
                    throw Exception("Solicitud inválida")
                }

                creatorId = event.creatorId
                eventTitle = event.title
                
                val request = JoinRequest(
                    userId = userId,
                    userNickname = answers["_nickname"] ?: "Usuario", 
                    answers = answers.filterKeys { !it.startsWith("_") },
                    createdAt = System.currentTimeMillis()
                )

                tx.update(
                    ref,
                    "pendingRequests",
                    com.google.firebase.firestore.FieldValue.arrayUnion(request)
                )
                true
            }.await()

            if (!transactionSuccess) {
                return Result.failure(Exception("Evento no encontrado"))
            }

            notificationRepository.sendNotification(
                AppNotification(
                    userId = creatorId,
                    title = "Nueva solicitud",
                    message = "Un usuario solicitó unirse a \"$eventTitle\"",
                    eventId = eventId,
                    type = NotificationType.JOIN_REQUEST_SENT
                )
            )

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    // UNIRSE DIRECTAMENTE (EVENTOS PÚBLICOS)
    // =========================================================
    suspend fun joinEventDirectly(
        eventId: String,
        userId: String
    ): Result<Unit> {
        return try {
            var eventTitle = ""
            var participantsToNotify = emptyList<String>()

            val transactionSuccess = firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(EventDto::class.java)?.toDomain()
                    ?: return@runTransaction false

                if (event.status != EventStatus.OPEN) {
                    throw Exception("El evento no acepta participantes")
                }
                
                if (!event.isPublic) {
                    throw Exception("Este evento requiere aprobación manual")
                }

                if (event.approvedParticipants.contains(userId)) {
                    // Already joined, do nothing
                    return@runTransaction true
                }
                
                if (event.approvedParticipants.size >= event.maxParticipants) {
                     throw Exception("Evento sin cupos")
                }

                tx.update(
                    ref,
                    "approvedParticipants",
                    com.google.firebase.firestore.FieldValue.arrayUnion(userId)
                )
                
                eventTitle = event.title
                // Store list of participants to notify (excluding the new one)
                participantsToNotify = event.approvedParticipants + event.creatorId
                true
            }.await()

            if (!transactionSuccess) {
                return Result.failure(Exception("Evento no encontrado"))
            }
            
            // Notify existing participants
            participantsToNotify.forEach { participantId ->
                if (participantId != userId) {
                    notificationRepository.sendNotification(
                        AppNotification(
                            userId = participantId,
                            title = "Nuevo participante",
                            message = "¡Un nuevo usuario se ha unido al evento \"$eventTitle\"!",
                            eventId = eventId,
                            type = NotificationType.JOIN_APPROVED // Fix reference
                        )
                    )
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    // SOLICITAR ACCESO
    // =========================================================
    suspend fun approveParticipant(
        eventId: String,
        userId: String
    ): Result<Unit> {
        return try {
            var eventTitle = ""
            var participantsToNotify = emptyList<String>()

            val transactionSuccess = firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(EventDto::class.java)?.toDomain()
                    ?: return@runTransaction false

                if (event.status != EventStatus.OPEN) {
                    throw Exception("Evento cerrado")
                }

                if (event.approvedParticipants.size >= event.maxParticipants) {
                    throw Exception("Evento sin cupos")
                }

                eventTitle = event.title

                // 🛡️ Sanitize first
                @Suppress("UNCHECKED_CAST")
                val safePending = (event.pendingRequests as? List<JoinRequest?>)?.filterNotNull() ?: emptyList()
                
                tx.update(
                    ref,
                    mapOf(
                        "pendingRequests" to safePending.filterNot {
                            it.userId == userId
                        },
                        "approvedParticipants" to
                                (event.approvedParticipants + userId)
                    )
                )
                
                // Collect existing participants + creator, sin duplicados
                participantsToNotify = (event.approvedParticipants + event.creatorId).distinct()
                true
            }.await()

            if (!transactionSuccess) {
                return Result.failure(Exception("Evento no encontrado"))
            }

            // 1. Notify the JOINER (solo al usuario aceptado)
            notificationRepository.sendNotification(
                AppNotification(
                    userId = userId,
                    title = "Solicitud aprobada",
                    message = "Has sido aceptado en \"$eventTitle\"",
                    eventId = eventId,
                    type = NotificationType.JOIN_APPROVED
                )
            )
            
            // 2. Notify OTHER participants (NO al usuario recién aceptado)
            participantsToNotify.forEach { participantId ->
                if (participantId != userId) {
                    notificationRepository.sendNotification(
                        AppNotification(
                            userId = participantId,
                            title = "Nuevo participante",
                            message = "¡Un nuevo usuario se ha unido al evento \"$eventTitle\"!",
                            eventId = eventId,
                            type = NotificationType.EVENT_UPDATE
                        )
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    // RECHAZAR PARTICIPANTE
    // =========================================================
    suspend fun rejectParticipant(
        eventId: String,
        userId: String
    ): Result<Unit> {
        return try {

            var eventTitle = ""
            var userNickname = ""

            val transactionSuccess = firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(EventDto::class.java)?.toDomain()
                    ?: return@runTransaction false

                eventTitle = event.title
                
                // 🛡️ Sanitize
                @Suppress("UNCHECKED_CAST")
                val safePending = (event.pendingRequests as? List<JoinRequest?>)?.filterNotNull() ?: emptyList()

                userNickname = safePending.firstOrNull { it.userId == userId }?.userNickname ?: "Usuario"

                tx.update(
                    ref,
                    mapOf(
                        "pendingRequests" to safePending.filterNot {
                            it.userId == userId
                        },
                        "rejectedParticipants" to
                                (event.rejectedParticipants + userId)
                    )
                )
                true
            }.await()

            if (!transactionSuccess) {
                return Result.failure(Exception("Evento no encontrado"))
            }

            notificationRepository.sendNotification(
                AppNotification(
                    userId = userId,
                    title = "Solicitud rechazada",
                    message = "Tu solicitud a \"$eventTitle\" fue rechazada",
                    eventId = eventId,
                    type = NotificationType.JOIN_REJECTED
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    // MODERACIÓN
    // =========================================================
    suspend fun cancelEvent(eventId: String, reason: String): Result<Unit> {
        return try {
            var eventTitle = ""
            var participantsToNotify = emptyList<String>()

            val transactionSuccess = firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(EventDto::class.java)?.toDomain()
                    ?: return@runTransaction false

                eventTitle = event.title
                participantsToNotify = event.approvedParticipants

                tx.update(
                    ref,
                    mapOf(
                        "status" to EventStatus.CANCELLED,
                        "cancelReason" to reason,
                        "cancelledAt" to System.currentTimeMillis()
                    )
                )
                true
            }.await()

            if (!transactionSuccess) {
                return Result.failure(Exception("Evento no encontrado"))
            }

            // Notify all participants
            participantsToNotify.forEach { participantId ->
                notificationRepository.sendNotification(
                    AppNotification(
                        userId = participantId,
                        title = "Evento Cancelado 🚫",
                        message = "El evento \"$eventTitle\" ha sido cancelado: $reason",
                        eventId = eventId,
                        type = NotificationType.EVENT_CANCELLED
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeEvent(eventId: String): Result<Unit> {
        return try {
            var eventTitle = ""
            var participantsToNotify = emptyList<String>()

            val transactionSuccess = firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(EventDto::class.java)?.toDomain()
                    ?: return@runTransaction false

                eventTitle = event.title
                participantsToNotify = event.approvedParticipants

                tx.update(ref, "status", EventStatus.CLOSED)
                true
            }.await()

            if (!transactionSuccess) {
                return Result.failure(Exception("Evento no encontrado"))
            }

            // Notify all participants
            participantsToNotify.forEach { participantId ->
                notificationRepository.sendNotification(
                    AppNotification(
                        userId = participantId,
                        title = "Evento Finalizado 🏁",
                        message = "El evento \"$eventTitle\" ha concluido. ¡No olvides calificar!",
                        eventId = eventId,
                        type = NotificationType.EVENT_CLOSED
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeParticipant(eventId: String, userId: String): Result<Unit> {
        return try {
            var eventTitle = ""

            val transactionSuccess = firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(EventDto::class.java)?.toDomain()
                    ?: return@runTransaction false

                if (event.creatorId == userId) {
                    throw Exception("No se puede eliminar al creador")
                }

                eventTitle = event.title

                // 🛡️ Sanitize Approved
                @Suppress("UNCHECKED_CAST")
                val safeApproved = (event.approvedParticipants as? List<String?>)?.filterNotNull() ?: emptyList()

                tx.update(
                    ref,
                    "approvedParticipants",
                    safeApproved.filterNot { it == userId }
                )
                true
            }.await()

            if (!transactionSuccess) {
                return Result.failure(Exception("Evento no encontrado"))
            }

            // 🔔 Notify the removed user
            notificationRepository.sendNotification(
                AppNotification(
                    userId = userId,
                    title = "Has sido eliminado del evento 🚫",
                    message = "El creador te ha eliminado de \"$eventTitle\"",
                    eventId = eventId,
                    type = NotificationType.REMOVED_FROM_EVENT
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    // ELIMINAR EVENTO DEFINITIVAMENTE
    // =========================================================
    suspend fun deleteEvent(eventId: String): Result<Unit> {
        return try {
            eventsCollection.document(eventId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

     // Lógica de feeds movida a MainFeedRepository

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
                .get()
                .await()
            
            var markedCount = 0
            val batch = com.google.firebase.firestore.FirebaseFirestore.getInstance().batch()
            
            eventsToMark.documents.forEach { doc ->
                val eventDto = doc.toObject(EventDto::class.java)
                if (eventDto != null) {
                    val event = eventDto.toDomain()
                    val ratingDeadline = event.endAt + (5 * 24 * 60 * 60 * 1000) // +5 días
                    
                    batch.update(doc.reference, mapOf(
                        "canBeRated" to true,
                        "ratingDeadline" to ratingDeadline
                    ))
                    markedCount++
                }
            }
            
            if (markedCount > 0) {
                batch.commit().await()
                android.util.Log.d("EventRepository", "Marked $markedCount events as ratable")
            }
            
            Result.success(markedCount)
        } catch (e: Exception) {
            android.util.Log.e("EventRepository", "Error marking events as ratable", e)
            Result.failure(e)
        }
    }

    // =========================================================
    // MONETIZACIÓN: BOOST EVENT (Round 42)
    // =========================================================
    suspend fun boostEvent(eventId: String, durationMs: Long): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val expiry = now + durationMs
            
            eventsCollection.document(eventId).update(
                mapOf(
                    "isBoosted" to true,
                    "boostExpiry" to expiry
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
