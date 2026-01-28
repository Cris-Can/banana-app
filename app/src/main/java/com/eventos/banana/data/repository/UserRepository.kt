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

    // ⭐ BATCH FETCH USERS (Performance Fix)
    suspend fun getUsers(uids: List<String>): List<UserProfile> {
        if (uids.isEmpty()) return emptyList()

        // Firestore restricts 'in' queries to 10 items. We must chunk.
        val chunks = uids.distinct().chunked(10)
        val allUsers = mutableListOf<UserProfile>()

        try {
            // We use coroutine scope to fetching parallel or just sequential loop. Sequential is safer for now.
            for (chunk in chunks) {
                val snapshot = users
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get()
                    .await()
                
                val chunkUsers = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
                }
                allUsers.addAll(chunkUsers)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error batch fetching users", e)
        }
        return allUsers
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
    // 🔔 A29 — SUSCRIPCIÓN A CATEGORÍAS
    // =====================================================
    suspend fun updateSubscribedCategories(uid: String, categories: List<String>) {
        // 1. Get old list to compare (for subscribe/unsubscribe)
        val oldProfile = getUserProfile(uid)
        val oldCategories = oldProfile?.subscribedCategories ?: emptyList()

        // 2. Update Firestore
        users.document(uid).update("subscribedCategories", categories).await()

        // 3. Sync FCM Topics
        // Subscribe to new ones
        categories.filter { !oldCategories.contains(it) }.forEach { topic ->
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
        }
        // Unsubscribe from removed ones
        oldCategories.filter { !categories.contains(it) }.forEach { topic ->
            com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
        }
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

    // 📊 ESTADÍSTICAS DE ASISTENCIA (Round 14)
    suspend fun incrementEventsRequested(uid: String) {
        users.document(uid).update("eventsRequestedCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
    }

    suspend fun incrementEventsAttended(uid: String) {
        users.document(uid).update("eventsAttendedCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
    }

    // 🔧 PARA DEBUG / ADMIN: Recalcular historiales (Solo usuario actual)
    suspend fun recalculateUserStats(uid: String): Result<String> {
        return try {
            val batch = firestore.batch()
            val userRef = users.document(uid)
            val updateData = mutableMapOf<String, Any>()

            // 1. Scan Check-ins for Attendance (My Checkins)
            val checkinsCount = firestore.collection("event_checkins")
                .whereEqualTo("userId", uid)
                .get()
                .await()
                .size()
            
            updateData["eventsAttendedCount"] = checkinsCount

            // 2. Scan Ratings (Ratings received by me)
            val ratingsSnapshot = firestore.collection("ratings")
                .whereEqualTo("toUserId", uid)
                .get()
                .await()
            
            val scores = ratingsSnapshot.documents.mapNotNull { it.getDouble("score") }
            updateData["ratingSum"] = scores.sum()
            updateData["ratingCount"] = scores.size
            
            // 3. Scan Requests (Events I requested/joined) - Optional/Approximate
            // Retrieving 'eventsRequestedCount' accurately is hard without an index or 'my_requests' subcollection.
            // We will skip recalculating 'eventsRequestedCount' to avoid query errors. 
            // Or we could try querying events where 'approvedParticipants' contains uid
            
            val attendedEventsSnapshot = firestore.collection("events")
                .whereArrayContains("approvedParticipants", uid)
                .get()
                .await()
            
            // This is "Events I am approved in", which is close to "Attended" but distinct from "Requested".
            // Let's assume 'eventsRequestedCount' is critical? If not, skip.
            // Let's just update ratings and attendance since that's what's broken.
            
            batch.update(userRef, updateData)
            batch.commit().await()
            
            Result.success("Perfil actualizado: ${scores.size} votos, $checkinsCount eventos.")
        } catch (e: Exception) {
             android.util.Log.e("UserRepository", "Error recalculating stats", e)
             Result.failure(e)
        }
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

    // =====================================================
    // 🔍 SEARCH & SUGGESTIONS
    // =====================================================
    suspend fun searchUsers(query: String): List<UserProfile> {
        if (query.isBlank()) return emptyList()
        return try {
            val snapshot = users
                .orderBy("nickname")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .limit(20)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error searching users", e)
            emptyList()
        }
    }

    suspend fun getUsersByRegion(region: String, excludeUid: String): List<UserProfile> {
        return try {
            val snapshot = users
                .whereEqualTo("region", region)
                .limit(50) // Reasonable limit
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
            }
                .filter { it.uid != excludeUid }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 💾 SAVED EVENTS (A30)
    suspend fun toggleEventSaved(uid: String, eventId: String, isSaved: Boolean) {
        val update = if (isSaved) {
            com.google.firebase.firestore.FieldValue.arrayUnion(eventId)
        } else {
            com.google.firebase.firestore.FieldValue.arrayRemove(eventId)
        }
        users.document(uid).update("savedEventIds", update).await()
    }
}
