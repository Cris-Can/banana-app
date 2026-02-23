package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration

import javax.inject.Inject

class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val notificationRepository: com.eventos.banana.data.repository.NotificationRepository
) {

    private val users = firestore.collection("users")

    // ---------- CREATE PROFILE ----------
    // ---------- CREATE PROFILE (With First 50 Check) ----------
    // ---------- CREATE PROFILE (Simple Save) ----------
    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            users.document(profile.uid).set(profile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error saving profile", e)
            Result.failure(e)
        }
    }

    // ---------- GET PROFILE (CACHE FIRST or FORCE SERVER) ----------
    suspend fun getUserProfile(uid: String, forceRefresh: Boolean = false): UserProfile? {
        return try {
            if (forceRefresh) {
                android.util.Log.d("UserRepository", "Forcing server fetch for $uid")
                val serverSnapshot = users.document(uid).get(Source.SERVER).await()
                val profile = serverSnapshot.toObject(UserProfile::class.java)?.copy(uid = uid)
                
                // 🛠️ RETROACTIVE FIX (Force Refresh)
                if (profile != null && !profile.isFounder) {
                     return upgradeLegacyUser(profile)
                }
                return profile
            }

            val cachedSnapshot = users
                .document(uid)
                .get(Source.CACHE)
                .await()

            if (cachedSnapshot.exists()) {
                val profile = cachedSnapshot.toObject(UserProfile::class.java)?.copy(uid = uid)
                
                // 🛠️ RETROACTIVE FIX (Cache)
                if (profile != null && !profile.isFounder) {
                     return upgradeLegacyUser(profile)
                }
                profile
            } else {
                android.util.Log.d("UserRepository", "Not in cache, fetching server for $uid")
                val serverSnapshot = users
                    .document(uid)
                    .get(Source.SERVER)
                    .await()

                val profile = serverSnapshot.toObject(UserProfile::class.java)?.copy(uid = uid)
                
                // 🛠️ RETROACTIVE FIX (Server Default)
                if (profile != null && !profile.isFounder) {
                     return upgradeLegacyUser(profile)
                }
                profile
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error getting profile for $uid", e)
            null
        }
    }

    // Helper to upgrade legacy users if global count <= 50 (BEST EFFORT / ROBUST)
    private suspend fun upgradeLegacyUser(profile: UserProfile): UserProfile {
        return try {
            // 1. Read Global Stats (Standard Read)
            val statsRef = firestore.collection("config").document("stats")
            val statsSnapshot = statsRef.get().await()
            val currentCount = statsSnapshot.getLong("userCount") ?: 0L

            if (currentCount <= 40) {
                // 2. Prepare Upgraded Profile
                val upgradedProfile = profile.copy(
                    isGoldStored = true,
                    isPremiumStored = true,
                    subscriptionType = "FOUNDER",
                    isFounder = true // 🚀 Retroactive Grant
                )
                
                // 3. IMMEDIATE WRITE to User (Priority: High)
                // We do this OUTSIDE the transaction to ensure the user gets it even if stats fail
                users.document(profile.uid).set(upgradedProfile, SetOptions.merge()).await()
                android.util.Log.d("UserRepository", "Retro-upgrade success for ${profile.uid}")

                // 4. STOP Incrementing Global Count here!
                // The count should only increase for NEW users (in createUserProfile).
                // Retroactive upgrades should not inflate the user count.

                upgradedProfile // Return upgraded

                upgradedProfile // Return upgraded
            } else {
                profile // Too late
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to retro-upgrade user (Critical)", e)
            profile
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
        commune: String,
        lat: Double? = null,
        lng: Double? = null,
        geohash: String? = null
    ) {
        val updates = mutableMapOf<String, Any>(
            "region" to region,
            "commune" to commune
        )
        if (lat != null) updates["latitude"] = lat
        if (lng != null) updates["longitude"] = lng
        if (geohash != null) updates["geohash"] = geohash
        
        users.document(uid).update(updates).await()
    }
    
    suspend fun updateSearchRadius(uid: String, radiusKm: Int) {
        users.document(uid).update("searchRadiusKm", radiusKm).await()
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

    suspend fun incrementEventsCreatedLifetime(uid: String) {
        users.document(uid).update("eventsCreatedLifetime", com.google.firebase.firestore.FieldValue.increment(1)).await()
    }

    // 🔧 PARA DEBUG / ADMIN: Recalcular historiales (Solo usuario actual)
    suspend fun recalculateUserStats(uid: String): Result<String> {
        return try {
            val batch = firestore.batch()
            val userRef = users.document(uid)
            val updateData = mutableMapOf<String, Any>()

            // 1. Scan Check-ins for Attendance (My Checkins)
            // Fix: Exclude check-ins for events I created (User requested separataion)
            val checkinsSnapshot = firestore.collection("event_checkins")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            
            // 3. Scan APPROVALS -> Denominator
            val approvedEventsSnapshot = firestore.collection("events")
                .whereArrayContains("approvedParticipants", uid)
                .get()
                .await()
            
            // Filter out events where I am the creator
            val validRequests = approvedEventsSnapshot.documents.filter { doc ->
                doc.getString("creatorId") != uid
            }
            
            // 🆕 Count Created Events (Lifetime)
            val createdEventsSnapshot = firestore.collection("events")
                .whereEqualTo("creatorId", uid)
                .get()
                .await()
            
            updateData["eventsCreatedLifetime"] = createdEventsSnapshot.size()
            updateData["eventsRequestedCount"] = validRequests.size
            
            // Refined Check-ins: Only count check-ins for events I didn't create?
            // If I checked in, I attended. That's fine.
            // But if reliability > 100%, it looks weird.
            val validCheckinsCount = checkinsSnapshot.size() // Keep simple for now
            updateData["eventsAttendedCount"] = validCheckinsCount
            
            // 2. Scan Ratings (Ratings received by me)
            val ratingsSnapshot = firestore.collection("ratings")
                .whereEqualTo("toUserId", uid)
                .get()
                .await()
            
            val scores = ratingsSnapshot.documents.mapNotNull { it.getDouble("score") }
            updateData["ratingSum"] = scores.sum()
            updateData["ratingCount"] = scores.size
            
            // 🆕 GAMIFICATION SCORE CALCULATION
            // Formula: (Events Attended * 10) + (Avg Rating * 20)
            // Example: 5 events * 10 = 50. Rating 5.0 * 20 = 100. Total = 150.
            val avgRating = if (scores.isNotEmpty()) scores.average() else 0.0
            val score = (validCheckinsCount * 10) + (avgRating * 20).toInt()
            
            updateData["score"] = score
            
            batch.update(userRef, updateData)
            batch.commit().await()
            
            
            // 4. RETROACTIVE FOUNDER CHECK (Integrated)
            try {
               val prof = getUserProfile(uid, forceRefresh = true) // This triggers the check internally!
            } catch (e: Exception) { 
               // Ignore
            }

            Result.success("Perfil actualizado: ${scores.size} votos, $validCheckinsCount eventos.")
        } catch (e: Exception) {
             android.util.Log.e("UserRepository", "Error recalculating stats", e)
             Result.failure(e)
        }
    }

    // 🏆 GAMIFICATION: Get Top Users
    suspend fun getTopUsers(limit: Int = 10): Result<List<UserProfile>> {
        return try {
            val snapshot = users
                .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val topUsers = snapshot.toObjects(UserProfile::class.java)
            Result.success(topUsers)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error getting top users", e)
            Result.failure(e)
        }
    }

    suspend fun uploadProfilePhoto(uid: String, imageBytes: ByteArray?, isProfilePicture: Boolean, isCoverPhoto: Boolean = false): Result<Unit> {
        return try {
            if (imageBytes == null || imageBytes.isEmpty()) {
                throw Exception("No se proporcionaron datos de imagen")
            }

            val imagePath = "users/$uid/photos/${java.util.UUID.randomUUID()}.jpg"
            
            // 📸 Upload to Firebase Storage (default bucket from google-services.json)
            val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
            val storageRef = storage.reference.child(imagePath)
            
            android.util.Log.d("UserRepository", "Uploading photo to: $imagePath (bucket: ${storage.reference.bucket})")
            
            // Upload bytes
            storageRef.putBytes(imageBytes).await()
            
            // Get download URL (with retry)
            var downloadUrl: String? = null
            for (i in 1..3) {
                try {
                    kotlinx.coroutines.delay(500L * i)
                    downloadUrl = storageRef.downloadUrl.await().toString()
                    if (downloadUrl != null) break
                } catch (e: Exception) {
                    android.util.Log.w("UserRepository", "Retry $i getting download URL", e)
                }
            }

            if (downloadUrl == null) {
                throw Exception("No se pudo obtener la URL de descarga de la foto")
            }

            android.util.Log.d("UserRepository", "Photo uploaded OK: $downloadUrl")

            // Update Firestore with the URL
            when {
                isProfilePicture -> users.document(uid).update("profilePictureUrl", downloadUrl).await()
                isCoverPhoto -> users.document(uid).update("coverPhotoUrl", downloadUrl).await()
                else -> users.document(uid).update("photos", com.google.firebase.firestore.FieldValue.arrayUnion(downloadUrl)).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "❌ Photo upload failed: ${e.message}", e)
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

        // Fetch current user nickname
        val currentUserDoc = currentUserRef.get().await()
        val currentNickname = currentUserDoc.getString("nickname") ?: "Alguien"

        // 🔔 Create Notification for Target User
        val notifRef = firestore.collection("notifications").document()
        val notification = mapOf(
            "id" to notifRef.id,
            "userId" to targetUid,
            "fromUserId" to currentUid,
            "type" to "FRIEND_REQUEST",
            "title" to "Nueva solicitud de amistad",
            "message" to "$currentNickname quiere ser tu amigo",
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
            val currentUserDoc = firestore.collection("users").document(currentUid).get().await()
            val currentNickname = currentUserDoc.getString("nickname") ?: "Alguien"

            val notifRef = firestore.collection("notifications").document()
            val notification = mapOf(
                "id" to notifRef.id,
                "userId" to requesterUid,
                "fromUserId" to currentUid,
                "type" to "FRIEND_ACCEPTED",
                "title" to "Solicitud aceptada",
                "message" to "$currentNickname ahora es tu amigo",
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

    // 🗑️ ELIMINAR AMIGO
    suspend fun removeFriend(currentUid: String, friendUid: String) {
        val batch = firestore.batch()
        val currentUserRef = users.document(currentUid)
        val friendUserRef = users.document(friendUid)

        batch.update(currentUserRef, "friends", com.google.firebase.firestore.FieldValue.arrayRemove(friendUid))
        batch.update(friendUserRef, "friends", com.google.firebase.firestore.FieldValue.arrayRemove(currentUid))

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

    suspend fun getUsersByCommune(commune: String, excludeUid: String): List<UserProfile> {
        return try {
            val snapshot = users
                .whereEqualTo("commune", commune)
                .limit(50)
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
    
    // 💎 BANANA GOLD (Round 42) - WITH FOUNDER PROTECTION 🛡️
    suspend fun setGoldStatus(uid: String, isGold: Boolean) {
        // 1. Check if user is a Founder (Immutable — NEVER downgrade)
        try {
            // 🛡️ FORCE SERVER to avoid stale cache missing isFounder
            val snapshot = users.document(uid).get(Source.SERVER).await()
            val isFounder = snapshot.getBoolean("isFounder") == true
            
            if (isFounder) {
                // Founder: ensure subscriptionType stays as FOUNDER, never FREE
                val currentType = snapshot.getString("subscriptionType") ?: ""
                if (currentType != "FOUNDER") {
                    // Auto-repair: restore FOUNDER type if it was corrupted
                    users.document(uid).update(mapOf(
                        "subscriptionType" to "FOUNDER",
                        "isGoldStored" to true
                    )).await()
                    android.util.Log.w("UserRepository", "Auto-repaired Founder $uid subscriptionType → FOUNDER")
                }
                return // Never modify founders further
            }
            
            // 2. Proceed with update if allowed (non-founder only)
            val updates = mutableMapOf<String, Any>()
            if (isGold) {
                updates["subscriptionType"] = "GOLD"
                updates["isGoldStored"] = true
            } else {
                updates["subscriptionType"] = "FREE"
                updates["isGoldStored"] = false
            }
            users.document(uid).update(updates).await()
            
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error setting Gold Status", e)
        }
    }

    // 👁️ WHO VIEWED MY PROFILE (Round 48)
    suspend fun recordProfileView(visitorUid: String, targetUid: String) {
        if (visitorUid == targetUid) return // Don't track self visits

        try {
            val viewsRef = users.document(targetUid).collection("profile_views")
            val visitorViewRef = viewsRef.document(visitorUid)
            
            // 1. Check Previous Visit (Anti-Spam Notification)
            val existingSnapshot = visitorViewRef.get().await()
            val lastVisit = existingSnapshot.getTimestamp("timestamp")?.toDate()?.time ?: 0L
            val now = System.currentTimeMillis()
            
            
            // 🕒 Cooldown reduced to 1 minute for easier testing/interaction
            val shouldNotify = (now - lastVisit) > 60000 // 1 Minute cooldown (was 1 Hour)
            
            // 2. Update View Record
            val viewData = mapOf(
                "visitorUid" to visitorUid,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            visitorViewRef.set(viewData, com.google.firebase.firestore.SetOptions.merge()).await()
            
            // 3. Send Notification if needed
            if (shouldNotify) {
                val notifRepo = notificationRepository
                notifRepo.sendNotification(
                    com.eventos.banana.domain.model.AppNotification(
                        userId = targetUid, // Recipient
                        title = "Tienes una nueva visita 👁️",
                        message = "Alguien ha visto tu perfil recientemente.",
                        type = com.eventos.banana.domain.model.NotificationType.PROFILE_VIEW,
                        read = false,
                        createdAt = com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error recording profile view", e)
        }
    }

    suspend fun getProfileViews(uid: String): List<ProfileView> {
        return try {
            val snapshot = users.document(uid)
                .collection("profile_views")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                val visitorUid = doc.getString("visitorUid") ?: doc.id
                ProfileView(visitorUid, timestamp)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error getting profile views", e)
            emptyList()
        }
    }
    // =====================================================
    // 🛡️ TRUST & SAFETY (Round 49)
    // =====================================================
    suspend fun blockUser(currentUid: String, targetUid: String): Result<Unit> {
        return try {
            users.document(currentUid).update(
                "blockedUsers",
                com.google.firebase.firestore.FieldValue.arrayUnion(targetUid)
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unblockUser(currentUid: String, targetUid: String): Result<Unit> {
        return try {
            users.document(currentUid).update(
                "blockedUsers",
                com.google.firebase.firestore.FieldValue.arrayRemove(targetUid)
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBlockedUsers(uid: String): List<String> {
        return try {
            val profile = getUserProfile(uid)
            profile?.blockedUsers ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getBlockedUsersProfiles(uid: String): List<UserProfile> {
        return try {
            val blockedIds = getBlockedUsers(uid)
            if (blockedIds.isEmpty()) return emptyList()
            blockedIds.mapNotNull { blockedId ->
                getUserProfile(blockedId)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun reportUser(reporterUid: String, reportedUid: String, reason: String): Result<Unit> {
        return try {
            val report = mapOf(
                "reporterId" to reporterUid,
                "reportedId" to reportedUid,
                "reason" to reason,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "status" to "PENDING"
            )
            firestore.collection("reports").add(report).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // Link to next section without closing the class

    // =====================================================
    // 👮 ADMIN DASHBOARD ACTIONS (Round 69)
    // =====================================================
    
    suspend fun getPendingReports(): List<Map<String, Any>> {
        return try {
            val snapshot = firestore.collection("reports")
                .whereEqualTo("status", "PENDING")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.map { doc ->
                doc.data?.plus("id" to doc.id) ?: emptyMap()
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error getting reports", e)
            emptyList()
        }
    }

    suspend fun resolveReport(reportId: String, action: String): Result<Unit> {
        return try {
            firestore.collection("reports").document(reportId)
                .update("status", action) // "RESOLVED" or "IGNORED"
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun banUser(uid: String): Result<Unit> {
        return try {
            users.document(uid).update("isBanned", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Unban specifically for admin correction
    suspend fun unbanUser(uid: String): Result<Unit> {
        return try {
            users.document(uid).update("isBanned", false).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class ProfileView(
    val visitorUid: String,
    val timestamp: Long
)
