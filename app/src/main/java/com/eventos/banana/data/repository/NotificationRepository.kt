package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.AppNotification
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

import javax.inject.Inject

class NotificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private val notificationsCollection = firestore.collection("notifications")

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

    // ✅ A9 — enviar notificación
    suspend fun sendNotification(notification: AppNotification) {
        try {
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
        } catch (e: Exception) {
            // Log error, but don't crash app if notification fails
            android.util.Log.e("NotificationRepo", "Failed to send notification: ${e.message}")
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
