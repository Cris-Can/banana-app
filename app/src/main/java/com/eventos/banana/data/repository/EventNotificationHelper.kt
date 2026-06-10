package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.AppNotification
import com.eventos.banana.domain.model.NotificationType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class EventNotificationHelper @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val notificationRepository: NotificationRepository
) {
    suspend fun notifyBatch(
        participantIds: List<String>,
        excludeUserId: String? = null,
        title: String,
        message: String,
        eventId: String,
        type: NotificationType
    ) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (participantIds.size > 500) {
            participantIds.forEach { pid ->
                if (pid != excludeUserId) {
                    notificationRepository.sendNotification(
                        AppNotification(
                            userId = pid,
                            title = title,
                            message = message,
                            eventId = eventId,
                            type = type
                        )
                    )
                }
            }
        } else {
            val batch = firestore.batch()
            var count = 0
            participantIds.forEach { pid ->
                if (pid != excludeUserId) {
                    val notifRef = firestore.collection("notifications").document()
                    val data = hashMapOf(
                        "id" to notifRef.id,
                        "userId" to pid,
                        "fromUserId" to currentUid,
                        "title" to title,
                        "message" to message,
                        "eventId" to eventId,
                        "read" to false,
                        "type" to type.name,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(notifRef, data)
                    count++
                }
            }
            if (count > 0) batch.commit().await()
        }
    }
}
