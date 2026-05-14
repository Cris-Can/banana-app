package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.AppNotification
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

import javax.inject.Inject

class NotificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private val notificationsCollection = firestore.collection("notifications")
    private val usersCollection = firestore.collection("users")

    fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        callbackFlow {
            android.util.Log.d("NotificationRepo", "👁️ Observing notifications for: $userId")

            val listener = notificationsCollection
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        android.util.Log.e("NotificationRepo", "❌ Listener error: ${error.message}")
                        close(error)
                        return@addSnapshotListener
                    }

                    val notifications = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(AppNotification::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            android.util.Log.e("NotificationRepo", "💥 Deserialization error for doc ${doc.id}: ${e.message}")
                            // Fallback manual
                            try {
                                val data = doc.data
                                AppNotification(
                                    id = doc.id,
                                    userId = data?.get("userId") as? String ?: "",
                                    title = data?.get("title") as? String ?: "",
                                    message = data?.get("message") as? String ?: "",
                                    eventId = data?.get("eventId") as? String,
                                    fromUserId = data?.get("fromUserId") as? String,
                                    read = data?.get("read") as? Boolean ?: false,
                                    type = com.eventos.banana.domain.model.NotificationType.valueOf(data?.get("type") as? String ?: "GENERIC"),
                                    createdAt = doc.getTimestamp("createdAt")?.toDate()
                                )
                            } catch (e2: Exception) {
                                null
                            }
                        }
                    } ?: emptyList()

                    // Ordenar manualmente para evitar requerir un índice compuesto en Firestore si hay filtros
                    val sorted = notifications.sortedByDescending { it.createdAt?.time ?: 0L }

                    trySend(sorted)
                }

            awaitClose { listener.remove() }
        }

    /**
     * ✅ A9 — Send notification with proper error handling
     * Returns Result to allow callers to handle failures appropriately
     */
    suspend fun sendNotification(notification: AppNotification): Result<Unit> {
        return try {
            val doc = notificationsCollection.document()
            
            // 🔥 Inject current user uid to satisfy Firestore Security Rules
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val safeFromUserId = notification.fromUserId ?: currentUid
            
            val data = hashMapOf(
                "id" to doc.id,
                "userId" to notification.userId,
                "fromUserId" to safeFromUserId,
                "title" to notification.title,
                "message" to notification.message,
                "eventId" to notification.eventId,
                "conversationId" to notification.conversationId,
                "read" to notification.read,
                "type" to notification.type.name,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            doc.set(data).await()
            
            Timber.d("Notification sent: %s to %s", notification.type.name, notification.userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send notification: %s", e.message)
            Result.failure(e)
        }
    }
    
    /**
     * 📧 Send notification only if user has enabled the corresponding preference
     * Respects notifyEventsByCommune and notifyByInterest settings
     */
    suspend fun sendNotificationWithPreferences(
        notification: AppNotification,
        requireEventsByCommune: Boolean = false,
        requireByInterest: Boolean = false
    ): Result<Unit> {
        return try {
            // Check user preferences
            val userDoc = usersCollection.document(notification.userId).get().await()
            if (!userDoc.exists()) {
                Timber.w("User %s not found for notification", notification.userId)
                return Result.failure(Exception("User not found"))
            }
            
            val userData = userDoc.data ?: emptyMap<String, Any>()
            
            // Check notifyEventsByCommune preference
            if (requireEventsByCommune) {
                val notifyByCommune = userData["notifyEventsByCommune"] as? Boolean ?: false
                if (!notifyByCommune) {
                    Timber.d("User %s has notifyEventsByCommune disabled, skipping", notification.userId)
                    return Result.success(Unit) // Success but not sent
                }
            }
            
            // Check notifyByInterest preference
            if (requireByInterest) {
                val notifyByInterest = userData["notifyByInterest"] as? Boolean ?: true // Default true
                if (!notifyByInterest) {
                    Timber.d("User %s has notifyByInterest disabled, skipping", notification.userId)
                    return Result.success(Unit) // Success but not sent
                }
            }
            
            // Send the notification
            sendNotification(notification)
        } catch (e: Exception) {
            Timber.e(e, "Error checking preferences for notification: %s", e.message)
            Result.failure(e)
        }
    }
    
    /**
     * 📧 Batch send notifications with preference checking
     * Useful for event notifications that should respect user preferences
     */
    suspend fun sendBatchNotificationWithPreferences(
        notifications: List<AppNotification>,
        requireEventsByCommune: Boolean = false,
        requireByInterest: Boolean = false
    ): Result<Int> {
        return try {
            // Get all user IDs
            val userIds = notifications.map { it.userId }.distinct()
            
            // Fetch all user preferences in batch
            val userDocs = mutableMapOf<String, Map<String, Any>>()
            val chunks = userIds.chunked(10) // Firestore 'in' query limit
            
            for (chunk in chunks) {
                val snapshot = usersCollection
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get()
                    .await()
                
                snapshot.documents.forEach { doc ->
                    userDocs[doc.id] = doc.data ?: emptyMap()
                }
            }
            
            // Filter notifications based on preferences
            val notificationsToSend = notifications.filter { notification ->
                val userData = userDocs[notification.userId] ?: return@filter true
                
                if (requireEventsByCommune) {
                    val notifyByCommune = userData["notifyEventsByCommune"] as? Boolean ?: false
                    if (!notifyByCommune) return@filter false
                }
                
                if (requireByInterest) {
                    val notifyByInterest = userData["notifyByInterest"] as? Boolean ?: true
                    if (!notifyByInterest) return@filter false
                }
                
                true
            }
            
            // Send filtered notifications
            var sentCount = 0
            for (notification in notificationsToSend) {
                sendNotification(notification).onSuccess { sentCount++ }
            }
            
            Timber.d("Batch notification: %d/%d sent", sentCount, notificationsToSend.size)
            Result.success(sentCount)
        } catch (e: Exception) {
            Timber.e(e, "Error in batch notification: %s", e.message)
            Result.failure(e)
        }
    }

    // ✅ A9.5 — marcar como leídas
    suspend fun markAllAsRead(userId: String) {
        val snapshot = notificationsCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("read", false)
            .get()
            .await()

        val batch = firestore.batch()

        snapshot.documents.forEach { doc ->
            batch.update(doc.reference, "read", true)
        }

        batch.commit().await()
    }
}