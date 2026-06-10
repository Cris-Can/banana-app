package com.eventos.banana.data.repository

import com.eventos.banana.core.security.RateLimitManager
import com.eventos.banana.domain.model.AppNotification
import com.eventos.banana.domain.model.NotificationType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

class UserSocialRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: com.google.firebase.functions.FirebaseFunctions,
    private val notificationRepository: NotificationRepository,
    private val rateLimitManager: RateLimitManager
) {
    companion object {
        private const val TAG = "UserSocialRepository"
    }
    private val users = firestore.collection("users")

    // =====================================================
    // 🤝 SISTEMA DE AMISTADES (BACKEND-DRIVEN VIA CLOUD FUNCTIONS)
    // =====================================================
    
    suspend fun sendFriendRequest(targetUid: String): Result<Unit> {
        return safeFunctionCall("sendFriendRequestV2", mapOf("targetUid" to targetUid))
    }

    suspend fun acceptFriendRequest(requesterUid: String): Result<Unit> {
        return safeFunctionCall("acceptFriendRequestV2", mapOf("requesterUid" to requesterUid))
    }

    suspend fun rejectFriendRequest(requesterUid: String): Result<Unit> {
        return safeFunctionCall("rejectFriendRequestV2", mapOf("requesterUid" to requesterUid))
    }

    suspend fun removeFriend(friendUid: String): Result<Unit> {
        return safeFunctionCall("removeFriendV2", mapOf("friendUid" to friendUid))
    }

    private suspend fun safeFunctionCall(functionName: String, data: Map<String, Any>): Result<Unit> {
        return try {
            Timber.d("[FRIEND_REP] Ejecutando $functionName con payload: $data")
            functions.getHttpsCallable(functionName).call(data).await()
            Timber.d("[FRIEND_REP] $functionName exitoso.")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[FRIEND_REP] Error en $functionName")
            Result.failure(e)
        }
    }

    // =====================================================
    // 💾 SAVED EVENTS
    // =====================================================
    suspend fun toggleEventSaved(uid: String, eventId: String, isSaved: Boolean) {
        val update = if (isSaved) {
            FieldValue.arrayUnion(eventId)
        } else {
            FieldValue.arrayRemove(eventId)
        }
        users.document(uid).update("savedEventIds", update).await()
    }

    // =====================================================
    // 👁️ WHO VIEWED MY PROFILE (With Rate Limiting)
    // =====================================================
    suspend fun recordProfileView(visitorUid: String, targetUid: String): Result<Unit> {
        if (visitorUid == targetUid) return Result.failure(Exception("Cannot view your own profile"))

        val rateLimitResult = rateLimitManager.checkRateLimit(RateLimitManager.ACTION_PROFILE_VIEW)
        if (!rateLimitResult.success) {
            return Result.failure(Exception(rateLimitResult.errorMessage ?: "Too many profile views"))
        }

        return try {
            val result = rateLimitManager.recordProfileView(targetUid)
            if (result.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.message ?: "Failed to record profile view"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProfileViews(uid: String): List<ProfileView> {
        return try {
            val snapshot = users.document(uid)
                .collection("profile_views")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                val visitorUid = doc.getString("visitorUid") ?: doc.id
                ProfileView(visitorUid, timestamp)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // =====================================================
    // 🛡️ TRUST & SAFETY
    // =====================================================
    suspend fun blockUser(currentUid: String, targetUid: String): Result<Unit> {
        return try {
            users.document(currentUid).update("blockedUsers", FieldValue.arrayUnion(targetUid)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unblockUser(currentUid: String, targetUid: String): Result<Unit> {
        return try {
            users.document(currentUid).update("blockedUsers", FieldValue.arrayRemove(targetUid)).await()
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
