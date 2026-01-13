package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val users = firestore.collection("users")

    // ---------- CREATE PROFILE ----------
    suspend fun createUserProfile(profile: UserProfile) {
        users
            .document(profile.uid)
            .set(profile)
            .await()
    }

    // ---------- GET PROFILE (CACHE FIRST) ----------
    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val cachedSnapshot = users
                .document(uid)
                .get(Source.CACHE)
                .await()

            if (cachedSnapshot.exists()) {
                cachedSnapshot.toObject(UserProfile::class.java)
            } else {
                val serverSnapshot = users
                    .document(uid)
                    .get(Source.SERVER)
                    .await()

                serverSnapshot.toObject(UserProfile::class.java)
            }
        } catch (e: Exception) {
            null
        }
    }

    // =====================================================
    // 🔔 A11.2 — FCM TOKEN
    // =====================================================
    suspend fun saveFcmToken(
        userId: String,
        token: String
    ) {
        users.document(userId)
            .set(
                mapOf(
                    "fcmToken" to token
                ),
                SetOptions.merge()
            )
            .await()
    }

    // =====================================================
    // 🧠 LEGACY — NOTIFICATION PREFERENCES (NO SE TOCA)
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

    // ---------- UPDATE NICKNAME ----------
    suspend fun updateNickname(uid: String, nickname: String) {
        users
            .document(uid)
            .update("nickname", nickname)
            .await()
    }

    // ---------- REALTIME PROFILE LISTENER ----------
    fun listenUserProfile(
        uid: String,
        onChange: (UserProfile) -> Unit,
        onError: () -> Unit
    ): ListenerRegistration {
        return users
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    onError()
                    return@addSnapshotListener
                }

                val profile = snapshot.toObject(UserProfile::class.java)
                if (profile != null) {
                    onChange(profile)
                }
            }
    }

    // ---------- UPDATE LOCATION ----------
    suspend fun updateLocation(
        uid: String,
        region: String,
        commune: String
    ) {
        users
            .document(uid)
            .update(
                mapOf(
                    "region" to region,
                    "commune" to commune
                )
            )
            .await()
    }

    // =====================================================
    // 🔔 A14.3 — PREFERENCIA EVENTOS POR COMUNA (NUEVO)
    // =====================================================
    suspend fun updateNotifyEventsByCommune(
        uid: String,
        enabled: Boolean
    ) {
        users
            .document(uid)
            .update(
                "notifyEventsByCommune",
                enabled
            )
            .await()
    }
}
