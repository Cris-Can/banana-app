package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.data.remote.model.UserProfileDto
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class UserGamificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val users = firestore.collection("users")

    // 📊 ESTADÍSTICAS DE ASISTENCIA
    suspend fun incrementEventsRequested(uid: String) {
        users.document(uid).update("eventsRequestedCount", FieldValue.increment(1)).await()
    }

    suspend fun incrementEventsAttended(uid: String) {
        users.document(uid).update("eventsAttendedCount", FieldValue.increment(1)).await()
    }

    suspend fun incrementEventsCreatedLifetime(uid: String) {
        users.document(uid).update("eventsCreatedLifetime", FieldValue.increment(1)).await()
    }

    // 🔧 PARA DEBUG / ADMIN: Recalcular historiales
    @Deprecated(
        message = "Aggregation is handled server-side by Cloud Function onRatingCreated. " +
            "Use only for admin/debug manual recalculation.",
        level = DeprecationLevel.WARNING
    )
    suspend fun recalculateUserStats(uid: String): Result<String> {
        return try {
            val batch = firestore.batch()
            val userRef = users.document(uid)
            val updateData = mutableMapOf<String, Any>()

            // 1. Scan Check-ins for Attendance (My Checkins)
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
            
            val validCheckinsCount = checkinsSnapshot.size()
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
            val avgRating = if (scores.isNotEmpty()) scores.average() else 0.0
            val score = (avgRating * 100).toInt()
            
            updateData["score"] = score
            
            batch.update(userRef, updateData)
            batch.commit().await()

            Result.success("Perfil actualizado: ${scores.size} votos, $validCheckinsCount eventos.")
        } catch (e: Exception) {
             android.util.Log.e("UserGamificationRepo", "Error recalculating stats", e)
             Result.failure(e)
        }
    }

    // 🏆 GAMIFICATION: Get Top Users (Most Active by Score)
    suspend fun getTopUsers(limit: Int = 20, startAfter: com.google.firebase.firestore.DocumentSnapshot? = null): Result<Pair<List<UserProfile>, com.google.firebase.firestore.DocumentSnapshot?>> {
        return try {
            var query = users
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                
            if (startAfter != null) {
                query = query.startAfter(startAfter)
            }
                
            val snapshot = query.get().await()
            val usersList = snapshot.toObjects(UserProfileDto::class.java).map { it.toDomain() }
            val lastVisible = if (snapshot.size() > 0) snapshot.documents[snapshot.size() - 1] else null
            Result.success(Pair(usersList, lastVisible))
        } catch (e: Exception) {
            android.util.Log.e("UserGamificationRepo", "Error getting top users by score", e)
            if (e.message?.contains("The query requires an index") == true) {
                Result.failure(Exception("El ranking se está configurando. Intenta en unos minutos."))
            } else {
                Result.failure(e)
            }
        }
    }

    // 🏆 REALTIME: Top users by score (snapshot listener)
    fun observeTopUsers(limit: Int = 20): Flow<List<UserProfile>> = callbackFlow {
        val listener = users
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("UserGamificationRepo", "observeTopUsers error", error)
                    if (error.message?.contains("The query requires an index") == true) {
                        close(Exception("El ranking se está configurando. Intenta en unos minutos."))
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(UserProfileDto::class.java)?.map { it.toDomain() } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getTopUsersByRating(limit: Int = 20, startAfter: com.google.firebase.firestore.DocumentSnapshot? = null): Result<Pair<List<UserProfile>, com.google.firebase.firestore.DocumentSnapshot?>> {
        return try {
            var query = users
                .orderBy("ratingSum", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                
            if (startAfter != null) {
                query = query.startAfter(startAfter)
            }

            val snapshot = query.get().await()
            val usersList = snapshot.toObjects(UserProfileDto::class.java).map { it.toDomain() }
            val lastVisible = if (snapshot.size() > 0) snapshot.documents[snapshot.size() - 1] else null
            
            Result.success(Pair(usersList, lastVisible))
        } catch (e: Exception) {
            android.util.Log.e("UserGamificationRepo", "Error getting top users by rating", e)
            if (e.message?.contains("The query requires an index") == true) {
                Result.failure(Exception("El ranking se está configurando. Intenta en unos minutos."))
            } else {
                Result.failure(e)
            }
        }
    }

    // ⭐ REALTIME: Top users by rating sum (snapshot listener)
    fun observeTopUsersByRating(limit: Int = 20): Flow<List<UserProfile>> = callbackFlow {
        val listener = users
            .orderBy("ratingSum", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("UserGamificationRepo", "observeTopUsersByRating error", error)
                    if (error.message?.contains("The query requires an index") == true) {
                        close(Exception("El ranking se está configurando. Intenta en unos minutos."))
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(UserProfileDto::class.java)?.map { it.toDomain() } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // Para no traer dependencia, le pasamos isFounder como abstracción desde la Fachada.
    suspend fun setGoldStatus(uid: String, isGold: Boolean, isFounder: Boolean, currentSubscriptionType: String) {
        try {
            if (isFounder) {
                // Founder: ensure subscriptionType stays as FOUNDER, never FREE
                if (currentSubscriptionType != "FOUNDER") {
                    // Auto-repair: restore FOUNDER type if it was corrupted
                    users.document(uid).update(mapOf(
                        "subscriptionType" to "FOUNDER",
                        "isGold" to true
                    )).await()
                    android.util.Log.w("UserGamificationRepo", "Auto-repaired Founder $uid subscriptionType → FOUNDER")
                }
                return // Never modify founders further
            }
            
            // 2. Proceed with update if allowed (non-founder only)
            val updates = mutableMapOf<String, Any>()
            if (isGold) {
                updates["subscriptionType"] = "GOLD"
                updates["isGold"] = true
            } else {
                updates["subscriptionType"] = "FREE"
                updates["isGold"] = false
            }
            users.document(uid).update(updates).await()
            
        } catch (e: Exception) {
            android.util.Log.e("UserGamificationRepo", "Error setting Gold Status", e)
        }
    }
}
