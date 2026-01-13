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
                .orderBy("createdAt")
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
        val doc = notificationsCollection.document()
        doc.set(notification.copy(id = doc.id)).await()
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
