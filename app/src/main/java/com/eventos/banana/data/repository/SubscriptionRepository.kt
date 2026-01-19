package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.SubscriptionType
import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class SubscriptionRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    // Limits
    companion object {
        const val FREE_LIMIT_CREATE_EVENT = 1
        const val FREE_LIMIT_JOIN_REQUEST = 3
    }

    /**
     * Checks if the user can create an event.
     * Handles Lazy Reset if cycle has passed.
     * @return Result<Boolean> true if allowed, false if limit reached.
     */
    suspend fun canCreateEvent(userId: String): Result<Boolean> {
        return try {
            val user = getUser(userId) ?: return Result.failure(Exception("User not found"))
            
            // LOGGING PRO: Check what we actually have
            android.util.Log.e("SubscriptionRepo", ">> CHECKING LIMITS FOR: ${user.subscriptionType}")
            android.util.Log.e("SubscriptionRepo", ">> COUNTS: Created=${user.eventsCreatedInCycle}, Joined=${user.joinRequestsInCycle}")
            android.util.Log.e("SubscriptionRepo", ">> DATE: CycleStart=${java.util.Date(user.currentCycleStartDate)}")

            val updatedUser = checkAndResetCycle(user)

            if (updatedUser.subscriptionType == SubscriptionType.PREMIUM) {
                android.util.Log.e("SubscriptionRepo", ">> RESULT: ALLOWED (PREMIUM)")
                Result.success(true)
            } else {
                if (updatedUser.eventsCreatedInCycle < FREE_LIMIT_CREATE_EVENT) {
                    android.util.Log.e("SubscriptionRepo", ">> RESULT: ALLOWED (${updatedUser.eventsCreatedInCycle} < $FREE_LIMIT_CREATE_EVENT)")
                    Result.success(true) // 0 < 1 -> OK
                } else {
                    android.util.Log.e("SubscriptionRepo", ">> RESULT: BLOCKED (${updatedUser.eventsCreatedInCycle} >= $FREE_LIMIT_CREATE_EVENT)")
                    Result.success(false)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionRepo", "Error checking limits", e)
            Result.failure(e)
        }
    }
    
    // ... same for canJoinEvent ...

    // --- Private Helpers ---

    private suspend fun getUser(userId: String): UserProfile? {
        return try {
            // FORCE SERVER to avoid stale cache
            val snapshot = usersCollection.document(userId).get(com.google.firebase.firestore.Source.SERVER).await()
            
            // LOG RAW DATA
            android.util.Log.e("SubscriptionRepo", ">> RAW FIRESTORE DATA: ${snapshot.data}")
            
            val profile = snapshot.toObject(UserProfile::class.java)?.copy(uid = userId)
            android.util.Log.e("SubscriptionRepo", ">> MAPPED OBJECT: type=${profile?.subscriptionType}, events=${profile?.eventsCreatedInCycle}")
            
            profile
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionRepo", "GetUser Failed", e)
            null
        }
    }

    /**
     * Checks if the user can join an event.
     * Handles Lazy Reset if cycle has passed.
     */
    suspend fun canJoinEvent(userId: String): Result<Boolean> {
        return try {
            val user = getUser(userId) ?: return Result.failure(Exception("User not found"))
            val updatedUser = checkAndResetCycle(user)

            if (updatedUser.subscriptionType == SubscriptionType.PREMIUM) {
                Result.success(true)
            } else {
                if (updatedUser.joinRequestsInCycle < FREE_LIMIT_JOIN_REQUEST) {
                    Result.success(true)
                } else {
                    Result.success(false)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun incrementCreateCount(userId: String) {
        try {
            usersCollection.document(userId).update(
                "eventsCreatedInCycle", com.google.firebase.firestore.FieldValue.increment(1)
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun incrementJoinCount(userId: String) {
        try {
            usersCollection.document(userId).update(
                "joinRequestsInCycle", com.google.firebase.firestore.FieldValue.increment(1)
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    /**
     * Checks if 30 days have passed since start of cycle.
     * If yes, resets counters in Firestore and returns updated object.
     * If no, returns original object.
     */
    private suspend fun checkAndResetCycle(user: UserProfile): UserProfile {
        val now = System.currentTimeMillis()
        val daysDiff = TimeUnit.MILLISECONDS.toDays(now - user.currentCycleStartDate)

        if (daysDiff >= 30) {
            // Lazy Reset
            val updates = mapOf(
                "currentCycleStartDate" to now,
                "eventsCreatedInCycle" to 0,
                "joinRequestsInCycle" to 0
            )
            usersCollection.document(user.uid).update(updates).await()
            
            return user.copy(
                currentCycleStartDate = now,
                eventsCreatedInCycle = 0,
                joinRequestsInCycle = 0
            )
        }
        return user
    }

    suspend fun getDebugStats(userId: String): String {
        return try {
            val user = getUser(userId) ?: return "User Not Found"
            // Reset just in case to show real "effective" stats
            val refreshed = checkAndResetCycle(user) 
            "Tipo: ${refreshed.subscriptionType}\n\nCreados: ${refreshed.eventsCreatedInCycle} / $FREE_LIMIT_CREATE_EVENT\n\nInicio Ciclo: ${java.util.Date(refreshed.currentCycleStartDate)}"
        } catch (e: Exception) {
            "Error stats: ${e.message}"
        }
    }

    suspend fun updateSubscriptionType(userId: String, type: SubscriptionType) {
        try {
            usersCollection.document(userId).update("subscriptionType", type).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
