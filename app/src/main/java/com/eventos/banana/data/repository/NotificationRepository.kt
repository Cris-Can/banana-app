package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.AppNotification
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepository {

    private val notificationsCollection =
        FirebaseFirestore.getInstance().collection("notifications")

    fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        callbackFlow {

            val listener = notificationsCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }

                    val notifications =
                        snapshot?.toObjects(AppNotification::class.java)
                            ?: emptyList()

                    trySend(notifications)
                }

            awaitClose { listener.remove() }
        }

    // ✅ A9 — enviar notificación
    suspend fun sendNotification(notification: AppNotification) {
        try {
            val doc = notificationsCollection.document()
            val data = hashMapOf(
                "id" to doc.id,
                "userId" to notification.userId,
                "title" to notification.title,
                "message" to notification.message,
                "eventId" to notification.eventId,
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

        val batch = FirebaseFirestore.getInstance().batch()

        snapshot.documents.forEach { doc ->
            batch.update(doc.reference, "read", true)
        }

        batch.commit().await()
    }
}
