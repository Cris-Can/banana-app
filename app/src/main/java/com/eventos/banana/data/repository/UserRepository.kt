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
        android.util.Log.d("UserRepository", "Creating profile in Firestore: uid=${profile.uid}, nickname=${profile.nickname}")
        users
            .document(profile.uid)
            .set(profile)
            .await()
        android.util.Log.d("UserRepository", "Profile successfully saved to Firestore for uid=${profile.uid}")
    }

    // ---------- GET PROFILE (CACHE FIRST or FORCE SERVER) ----------
    suspend fun getUserProfile(uid: String, forceRefresh: Boolean = false): UserProfile? {
        return try {
            if (forceRefresh) {
                android.util.Log.d("UserRepository", "Forcing server fetch for $uid")
                val serverSnapshot = users.document(uid).get(Source.SERVER).await()
                return serverSnapshot.toObject(UserProfile::class.java)?.copy(uid = uid)
            }

            val cachedSnapshot = users
                .document(uid)
                .get(Source.CACHE)
                .await()

            if (cachedSnapshot.exists()) {
                val profile = cachedSnapshot.toObject(UserProfile::class.java)?.copy(uid = uid)
                // Log if profile found in cache
                // android.util.Log.d("UserRepository", "Found in cache: ${profile?.nickname}")
                profile
            } else {
                android.util.Log.d("UserRepository", "Not in cache, fetching server for $uid")
                val serverSnapshot = users
                    .document(uid)
                    .get(Source.SERVER)
                    .await()

                serverSnapshot.toObject(UserProfile::class.java)?.copy(uid = uid)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error getting profile for $uid", e)
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

    suspend fun updateVerificationStatus(uid: String, verified: Boolean) {
        users.document(uid).set(mapOf("isVerified" to verified), SetOptions.merge()).await()
    }

    // =====================================================
    // 🔔 A14.3 — PREFERENCIA EVENTOS POR COMUNA (NUEVO)
    // =====================================================
    suspend fun updateNotifyEventsByCommune(
        uid: String,
        enabled: Boolean,
        region: String?,
        commune: String?
    ) {
        users
            .document(uid)
            .update(
                "notifyEventsByCommune",
                enabled
            )
            .await()
            
        // Manage topic subscription
        if (commune != null) {
            val topicName = "events_${commune.replace(" ", "_")}"
            if (enabled) {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(topicName).await()
            } else {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(topicName).await()
            }
        }
    }

    suspend fun updateNotifyEventWall(uid: String, enabled: Boolean) {
        users.document(uid).update("notifyEventWall", enabled).await()
    }


    // =====================================================
    // 🎨 A20 — SOCIAL PROFILE
    // =====================================================
    suspend fun updateSocialProfile(
        uid: String,
        aboutMe: String,
        interests: List<String>
    ) {
        users.document(uid)
            .update(
                mapOf(
                    "aboutMe" to aboutMe,
                    "interests" to interests
                )
            )
            .await()
    }

    suspend fun updateAppTheme(uid: String, theme: String) {
        users.document(uid).update("appTheme", theme).await()
    }

    suspend fun uploadProfilePhoto(uid: String, imageBytes: ByteArray?, isProfilePicture: Boolean): Result<Unit> {
        return try {
            val imagePath = "users/$uid/photos/${java.util.UUID.randomUUID()}.jpg"
                // Since `UserRepository` doesn't have a `Context`, this is a major issue with the snippet.
                // I cannot add a `Context` parameter without making an "unrelated edit".
                // The only way to make it syntactically correct *without* adding `Context` is to make `imageBytes` a dummy,
                // which would make the code non-functional.

                // Given the constraint "Make sure to incorporate the change in a way so that the resulting file is syntactically correct.",
                // and "without making any unrelated edits", I must assume `imageBytes` is somehow available or the user will fix it.
                // However, the snippet *itself* introduces `imageBytes`.

                // Let's assume the user wants to replace the `putFile(uri)` with `putBytes(imageBytes)` and the new retry logic.
                // I will define `imageBytes` as `uri.readBytes()` (conceptually) and `imageUrl` as `downloadUrl`.
                // This will require a `Context` to read `uri`. I will add a comment about this.

                // Final plan:
                // 1. Keep `val projectID` and `val imagePath`.
                // 2. Remove the `buckets` list and the `for (bucket in buckets)` loop.
            
            var downloadUrl: String? = null

            if (imageBytes != null && imageBytes.isNotEmpty()) {
                val projectID = "bananaapp-aa46e"
                val buckets = listOf(
                    "default",
                    "$projectID.appspot.com",
                    "$projectID.firebasestorage.app"
                )
                
                var lastEx: Exception? = null

                for (bucketName in buckets) {
                    try {
                        val storageInstance = if (bucketName == "default") {
                            com.google.firebase.storage.FirebaseStorage.getInstance()
                        } else {
                            com.google.firebase.storage.FirebaseStorage.getInstance("gs://$bucketName")
                        }
                        
                        val storageRef = storageInstance.reference.child(imagePath)
                        
                        // Upload
                        storageRef.putBytes(imageBytes).await()
                        
                        // URL
                        for (i in 1..3) {
                            try {
                                kotlinx.coroutines.delay(500L * i)
                                downloadUrl = storageRef.downloadUrl.await().toString()
                                if (downloadUrl != null) break
                            } catch (e: Exception) { }
                        }

                        if (downloadUrl != null) break 
                    } catch (e: Exception) {
                        lastEx = e
                        if (e is com.google.firebase.storage.StorageException && e.errorCode == -13010) {
                            continue
                        } else {
                            break
                        }
                    }
                }

                if (downloadUrl == null) {
                    throw Exception("Fallo en foto de perfil. Revisa que Storage esté activado: ${lastEx?.message}")
                }
            } else {
                throw Exception("No se proporcionaron datos de imagen")
            }

            if (isProfilePicture) {
                users.document(uid).update("profilePictureUrl", downloadUrl).await()
            } else {
                users.document(uid).update("photos", com.google.firebase.firestore.FieldValue.arrayUnion(downloadUrl)).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePhoto(uid: String, photoUrl: String) {
        users.document(uid).update(
            "photos",
            com.google.firebase.firestore.FieldValue.arrayRemove(photoUrl)
        ).await()
    }

    // =====================================================
    // 🤝 A20 — FRIENDS SYSTEM
    // =====================================================
    suspend fun sendFriendRequest(currentUid: String, targetUid: String) {
        val batch = firestore.batch()

        val currentUserRef = users.document(currentUid)
        val targetUserRef = users.document(targetUid)

        // Add to 'sent' list of current user
        batch.update(currentUserRef, "friendRequestsSent", com.google.firebase.firestore.FieldValue.arrayUnion(targetUid))
        
        // Add to 'received' list of target user
        batch.update(targetUserRef, "friendRequestsReceived", com.google.firebase.firestore.FieldValue.arrayUnion(currentUid))

        // 🔔 Create Notification for Target User
        val notifRef = firestore.collection("notifications").document()
        val notification = mapOf(
            "id" to notifRef.id,
            "userId" to targetUid,
            "fromUserId" to currentUid,
            "type" to "FRIEND_REQUEST",
            "title" to "Nueva solicitud de amistad",
            "message" to "Alguien quiere ser tu amigo", // We can enhance this if we fetch nickname
            "read" to false,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        batch.set(notifRef, notification)

        batch.commit().await()
    }

    suspend fun acceptFriendRequest(currentUid: String, requesterUid: String) {
        val batch = firestore.batch()
        val currentUserRef = users.document(currentUid)
        val requesterRef = users.document(requesterUid)

        // 1. Add to 'friends' list for both
        batch.update(currentUserRef, "friends", com.google.firebase.firestore.FieldValue.arrayUnion(requesterUid))
        batch.update(requesterRef, "friends", com.google.firebase.firestore.FieldValue.arrayUnion(currentUid))

        // 2. Remove from 'requests' lists
        batch.update(currentUserRef, "friendRequestsReceived", com.google.firebase.firestore.FieldValue.arrayRemove(requesterUid))
        batch.update(requesterRef, "friendRequestsSent", com.google.firebase.firestore.FieldValue.arrayRemove(currentUid))

        batch.commit().await()

        // 🔔 Create Notification for Requester (Accepted) - Separate batch or async
        try {
            val notifRef = firestore.collection("notifications").document()
            val notification = mapOf(
                "id" to notifRef.id,
                "userId" to requesterUid,
                "fromUserId" to currentUid,
                "type" to "FRIEND_ACCEPTED",
                "title" to "Solicitud aceptada",
                "message" to "Ahora son amigos",
                "read" to false,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            notifRef.set(notification).await()
        } catch (e: Exception) {
            // Ignore notification failure
        }
    }

    suspend fun rejectFriendRequest(currentUid: String, requesterUid: String) {
        val batch = firestore.batch()
        val currentUserRef = users.document(currentUid)
        val requesterRef = users.document(requesterUid)

        batch.update(currentUserRef, "friendRequestsReceived", com.google.firebase.firestore.FieldValue.arrayRemove(requesterUid))
        batch.update(requesterRef, "friendRequestsSent", com.google.firebase.firestore.FieldValue.arrayRemove(currentUid))

        batch.commit().await()
    }
}
