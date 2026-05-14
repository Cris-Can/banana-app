package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserMessagingRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val users = firestore.collection("users")

    suspend fun saveFcmToken(userId: String, token: String) {
        users.document(userId)
            .set(
                mapOf(
                    "fcmToken" to token
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun verifyAndSyncFcmToken(userId: String) {
        try {
            // 1. Get current device token
            val deviceToken = FirebaseMessaging.getInstance()
                .token.await()

            // 2. Get stored token from Firestore (from cache first, fast)
            val storedToken = users.document(userId)
                .get(com.google.firebase.firestore.Source.CACHE)
                .await()
                .getString("fcmToken")

            // 3. Compare and sync if different
            if (deviceToken != storedToken) {
                saveFcmToken(userId, deviceToken)
                android.util.Log.d("UserMessagingRepository", "🔔 FCM Token SYNCED (was stale). User: $userId")
            } else {
                android.util.Log.d("UserMessagingRepository", "🔔 FCM Token OK (matches). User: $userId")
            }
        } catch (e: Exception) {
            // If cache miss, fallback to force save
            try {
                val deviceToken = FirebaseMessaging.getInstance()
                    .token.await()
                saveFcmToken(userId, deviceToken)
                android.util.Log.d("UserMessagingRepository", "🔔 FCM Token force-saved (cache miss). User: $userId")
            } catch (e2: Exception) {
                android.util.Log.e("UserMessagingRepository", "🔔 FCM Token sync FAILED: ${e2.message}")
            }
        }
    }

    // =====================================================
    // ⚙️ PREFERENCIAS Y NOTIFICACIONES LOCALES FCM
    // =====================================================
    suspend fun saveNotificationPreferences(
        userId: String,
        region: String,
        commune: String,
        eventsInMyCommune: Boolean
    ) {
        users.document(userId)
            .set(
                mapOf(
                    "notificationPreferences" to mapOf(
                        "eventsInMyCommune" to eventsInMyCommune,
                        "region" to region,
                        "commune" to commune
                    )
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun updateNotifyEventsByCommune(uid: String, enabled: Boolean, region: String?, commune: String?) {
        users.document(uid).update("notifyEventsByCommune", enabled).await()
    }

    suspend fun updateNotifyEventWall(uid: String, enabled: Boolean) {
        users.document(uid).update("notifyEventWall", enabled).await()
    }

    suspend fun syncNotificationTopics(profile: UserProfile) {
        try {
            val messaging = FirebaseMessaging.getInstance()
            
            // 1. Zone Topic (Commune)
            val commune = profile.commune
            if (!commune.isNullOrBlank()) {
                val topicName = "events_${commune.replace(" ", "_")}"
                if (profile.notifyEventsByCommune) {
                    messaging.subscribeToTopic(topicName).await()
                    android.util.Log.d("UserMessagingRepository", "Subscribed to Topic: $topicName")
                } else {
                    messaging.unsubscribeFromTopic(topicName).await()
                    android.util.Log.d("UserMessagingRepository", "Unsubscribed from Topic: $topicName")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UserMessagingRepository", "Error syncing topics: ${e.message}")
        }
    }

    suspend fun updateSubscribedCategories(uid: String, categories: List<String>, oldCategories: List<String>) {
        users.document(uid).update("subscribedCategories", categories).await()

        categories.filter { !oldCategories.contains(it) }.forEach { topic ->
            FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
        }
        
        oldCategories.filter { !categories.contains(it) }.forEach { topic ->
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
        }
    }
}
