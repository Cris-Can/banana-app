package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class EventRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val eventsCollection = firestore.collection("events")

    private val notificationRepository = NotificationRepository()

    suspend fun createEvent(event: Event, imageBytes: ByteArray? = null): Result<Unit> {
        val docRef = eventsCollection.document()
        val imagePath = "events_covers/${docRef.id}.jpg"
        
        return try {
            var imageUrl: String? = null
            
            if (imageBytes != null && imageBytes.isNotEmpty()) {
                val projectID = "bananaapp-aa46e"
                val buckets = listOf(
                    "default", // tries default from JSON
                    "$projectID.appspot.com",
                    "$projectID.firebasestorage.app"
                )
                
                var lastStorageEx: Exception? = null

                for (bucketName in buckets) {
                    try {
                        val storageInstance = if (bucketName == "default") {
                            com.google.firebase.storage.FirebaseStorage.getInstance()
                        } else {
                            com.google.firebase.storage.FirebaseStorage.getInstance("gs://$bucketName")
                        }
                        
                        val storageRef = storageInstance.reference.child(imagePath)
                        
                        // Try Upload
                        storageRef.putBytes(imageBytes).await()
                        
                        // Try Get URL
                        for (i in 1..3) {
                            try {
                                kotlinx.coroutines.delay(500L * i)
                                imageUrl = storageRef.downloadUrl.await().toString()
                                if (imageUrl != null) break
                            } catch (e: Exception) { /* retry */ }
                        }
                        
                        if (imageUrl != null) break // Success!
                        
                    } catch (e: Exception) {
                        lastStorageEx = e
                        // if it's 404, try next bucket
                        if (e is com.google.firebase.storage.StorageException && e.errorCode == -13010) {
                            continue
                        } else {
                            break // other error, abort loop
                        }
                    }
                }

                if (imageUrl == null) {
                    val msg = (lastStorageEx as? com.google.firebase.storage.StorageException)?.let {
                        "Error Storage (-13010): El bucket no existe o no está activado en la Consola. Bucket intentado: ${it.message}"
                    } ?: lastStorageEx?.message ?: "Error desconocido en subida"
                    throw Exception("Fallo al subir foto. Asegúrate de que 'Storage' esté activado en Firebase Console: $msg")
                }
            }

            val eventWithId = event.copy(
                id = docRef.id,
                imageUrl = imageUrl,
                createdAt = System.currentTimeMillis(),
                approvedParticipants = listOf(event.creatorId),
                pendingRequests = emptyList(),
                rejectedParticipants = emptyList()
            )

            docRef.set(eventWithId).await()
            
            // 🔔 NOTIFY ZONE USERS (Client-side implementation)
            try {
                notifyZoneUsers(eventWithId)
            } catch (e: Exception) {
                // Log error but don't fail event creation
                android.util.Log.e("EventRepository", "Failed to notify zone users: ${e.message}")
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun notifyZoneUsers(event: Event) {
        // Query users in the same commune who want notifications
        val snapshot = firestore.collection("users")
            .whereEqualTo("commune", event.commune)
            .whereEqualTo("notifyEventsByCommune", true)
            .get()
            .await()

        snapshot.documents.forEach { doc ->
            val userId = doc.id
            // Don't notify creator
            if (userId != event.creatorId) {
                notificationRepository.sendNotification(
                    AppNotification(
                        userId = userId,
                        title = "Nuevo evento en ${event.commune}",
                        message = event.title,
                        eventId = event.id,
                        type = NotificationType.EVENT_CREATED
                    )
                )
            }
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
                        // más adelante:
                        // "isArchived" to true,
                        // "archivedAt" to now
                    )
                ).await()
            }

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

    fun listenToEvent(eventId: String): Flow<Event> = callbackFlow {
        val listener = eventsCollection.document(eventId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("EventRepository", "listenToEvent error: ${error.message}", error)
                    return@addSnapshotListener
                }
                val event = snapshot?.toObject(Event::class.java)
                if (event != null) {
                    trySend(event)
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
            val event = snapshot.toObject(Event::class.java)
                ?: return Result.failure(Exception("Evento no encontrado"))

            Result.success(event)
        } catch (e: Exception) {
            Result.failure(e)
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

            firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

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
                
                // Fetch user info to include in request (A23 enhancement)
                // Since this is inside runTransaction, we should ideally have the nickname before.
                // But for now we'll pass it from the ViewModel or use the provided logic.
                // For simplicity, I'll assume the nickname is provided or we use a placeholder.
                
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
            }.await()

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

            firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                if (event.status != EventStatus.OPEN) {
                    throw Exception("El evento no acepta participantes")
                }
                
                if (!event.isPublic) {
                    throw Exception("Este evento requiere aprobación manual")
                }

                if (event.approvedParticipants.contains(userId)) {
                    // Already joined, do nothing
                    return@runTransaction
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
            }.await()
            
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

            firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

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
                
                // Collect existing participants + creator
                participantsToNotify = event.approvedParticipants + event.creatorId
            }.await()

            // 1. Notify the JOINER
            notificationRepository.sendNotification(
                AppNotification(
                    userId = userId,
                    title = "Solicitud aprobada",
                    message = "Has sido aceptado en \"$eventTitle\"",
                    eventId = eventId,
                    type = NotificationType.JOIN_APPROVED
                )
            )
            
            // 2. Notify OTHER participants
            participantsToNotify.forEach { participantId ->
                if (participantId != userId) {
                    notificationRepository.sendNotification(
                        AppNotification(
                            userId = participantId,
                            title = "Nuevo participante",
                            message = "¡Un nuevo usuario se ha unido al evento \"$eventTitle\"!",
                            eventId = eventId,
                            type = NotificationType.JOIN_APPROVED
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

            firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

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
            }.await()

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
            firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                tx.update(
                    ref,
                    mapOf(
                        "status" to EventStatus.CANCELLED,
                        "cancelReason" to reason,
                        "cancelledAt" to System.currentTimeMillis()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeEvent(eventId: String): Result<Unit> {
        return try {
            eventsCollection.document(eventId)
                .update("status", EventStatus.CLOSED)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeParticipant(eventId: String, userId: String): Result<Unit> {
        return try {
            firestore.runTransaction { tx ->
                val ref = eventsCollection.document(eventId)
                val event = tx.get(ref).toObject(Event::class.java)
                    ?: throw Exception("Evento no encontrado")

                if (event.creatorId == userId) {
                    throw Exception("No se puede eliminar al creador")
                }

                // 🛡️ Sanitize Approved
                @Suppress("UNCHECKED_CAST")
                val safeApproved = (event.approvedParticipants as? List<String?>)?.filterNotNull() ?: emptyList()

                tx.update(
                    ref,
                    "approvedParticipants",
                    safeApproved.filterNot { it == userId }
                )
            }.await()

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

    // =========================================================
    // OBSERVAR EVENTOS (REALTIME) - GEOHASH SUPPORT
    // =========================================================
    // =========================================================
    // OBSERVAR EVENTOS (REALTIME) - GEOHASH SUPPORT
    // =========================================================
    fun observeNearbyEvents(geohashPrefix: String? = null, commune: String? = null, region: String? = null): Flow<List<Event>> = callbackFlow {

        val collectionRef = eventsCollection

        var query: com.google.firebase.firestore.Query = collectionRef

        if (!commune.isNullOrBlank()) {
            // 🏙️ 1. Filter by Commune (Highest Priority)
            query = query.whereEqualTo("commune", commune)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100)
        } else if (!region.isNullOrBlank()) {
            // 🗺️ 2. Filter by Region (New)
            query = query.whereEqualTo("region", region)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100)
        } else if (!geohashPrefix.isNullOrEmpty()) {
            // 📍 3. Filter by Geohash (GPS)
            val endParams = geohashPrefix + "~"
            query = query
                .orderBy("geohash")
                .startAt(geohashPrefix)
                .endAt(endParams)
                .limit(100)
        } else {
            // 🌍 4. Global Fallback
            query = query.orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(500) // Increased limit to support "All Radius" global search strategy
        }

        val listener = query.addSnapshotListener { snapshot, error ->

            if (error != null) {
                android.util.Log.e("EventRepository", "observeNearbyEvents error: ${error.message}", error)
                trySend(emptyList())
                return@addSnapshotListener
            }

            val now = System.currentTimeMillis()

            val events = snapshot
                ?.toObjects(Event::class.java)
                ?.filter {
                    // Client-side filtering
                    it.status == EventStatus.OPEN &&
                    it.endAt > now
                }
                ?.sortedWith(
                    compareByDescending<Event> { it.isBoosted && it.boostExpiry > now }
                        .thenByDescending { it.createdAt }
                )
                ?: emptyList()

            trySend(events)
        }

        awaitClose { listener.remove() }
    }
    
    // Deprecated: Kept for compatibility if needed, but pointing to new logic
    fun observeEvents() = observeNearbyEvents(null, null, null)

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
                val event = doc.toObject(Event::class.java)
                if (event != null) {
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

    // =========================================================
    // MIGRATION TOOL (A29) - Fix old events
    // =========================================================
    suspend fun migrateEventsToGeohash(): Result<Int> {
        return try {
            // 1. Get ALL events (or those missing geohash if possible, but Firestore doesn't support "missing field" query easily without custom index on null)
            // Safer to just get all Open events or all events. Let's get ALL for now (assuming DB size isn't huge yet).
            val snapshot = eventsCollection.get().await()
            val batch = firestore.batch()
            var count = 0
            
            snapshot.documents.forEach { doc ->
                val lat = doc.getDouble("latitude")
                val lng = doc.getDouble("longitude")
                val currentHash = doc.getString("geohash")
                
                if (lat != null && lng != null && currentHash == null) {
                    val newHash = com.eventos.banana.util.GeohashUtils.encode(lat, lng, 9)
                    batch.update(doc.reference, "geohash", newHash)
                    count++
                }
            }
            
            if (count > 0) {
                batch.commit().await()
            }
            
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
