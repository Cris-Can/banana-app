package com.eventos.banana.data.repository

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

    data class UserLimitStats(
        val subscriptionType: String,
        val eventsCreated: Int,
        val adsUnlocked: Int,
        val limit: Int
    )

    /**
     * Checks if the user can create an event.
     * Handles Lazy Reset if cycle has passed.
     * @return Result<Boolean> true if allowed, false if limit reached.
     */
    suspend fun canCreateEvent(userId: String): Result<Boolean> {
        return try {
            val user = getUser(userId) ?: return Result.failure(Exception("User not found"))
            
            // LOGGING PRO: Check what we actually have
            android.util.Log.e("SubscriptionRepo", ">> CHECKING LIMITS FOR: ${user.subscriptionType} | isFounder=${user.isFounder} | isGold=${user.isGold}")
            android.util.Log.e("SubscriptionRepo", ">> COUNTS: Created=${user.eventsCreatedInCycle}, Joined=${user.joinRequestsInCycle}")
            android.util.Log.e("SubscriptionRepo", ">> DATE: CycleStart=${java.util.Date(user.currentCycleStartDate)}")

            val updatedUser = checkAndResetCycle(user)

            if (updatedUser.subscriptionType == "GOLD" || updatedUser.subscriptionType == "FOUNDER" || updatedUser.isFounder) {
                android.util.Log.e("SubscriptionRepo", ">> RESULT: ALLOWED (GOLD/FOUNDER)")
                Result.success(true)
            } else {
                val effectiveLimit = FREE_LIMIT_CREATE_EVENT + updatedUser.adEventsUnlocked
                
                if (updatedUser.eventsCreatedInCycle < effectiveLimit) {
                    android.util.Log.e("SubscriptionRepo", ">> RESULT: ALLOWED (${updatedUser.eventsCreatedInCycle} < $effectiveLimit)")
                    Result.success(true)
                } else {
                    android.util.Log.e("SubscriptionRepo", ">> RESULT: BLOCKED (${updatedUser.eventsCreatedInCycle} >= $effectiveLimit)")
                    Result.success(false)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionRepo", "Error checking limits", e)
            Result.failure(e)
        }
    }
    
    // ... same for canJoinEvent ...

    // --- Private Helpers NO MORE ---
    
    suspend fun getUser(userId: String): UserProfile? {
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

            if (updatedUser.subscriptionType == "GOLD" || updatedUser.subscriptionType == "FOUNDER" || updatedUser.isFounder) {
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
                "joinRequestsInCycle" to 0,
                "adEventsUnlocked" to 0,
                "adsWatchedProgress" to 0
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

    suspend fun getUserLimitStats(userId: String): UserLimitStats? {
        return try {
            val user = getUser(userId) ?: return null
            val refreshed = checkAndResetCycle(user)
            UserLimitStats(
                subscriptionType = refreshed.subscriptionType,
                eventsCreated = refreshed.eventsCreatedInCycle,
                adsUnlocked = refreshed.adEventsUnlocked,
                limit = FREE_LIMIT_CREATE_EVENT // The base limit, UI calculates effective
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getDebugStats(userId: String): String {
        return try {
            val user = getUser(userId) ?: return "User Not Found"
            // Reset just in case to show real "effective" stats
            val refreshed = checkAndResetCycle(user) 
            "Tipo: ${refreshed.subscriptionType}\n" +
            "Creados: ${refreshed.eventsCreatedInCycle} / ${FREE_LIMIT_CREATE_EVENT + refreshed.adEventsUnlocked}\n" +
            "Extra Ads: ${refreshed.adEventsUnlocked}\n" +
            "Inicio Ciclo: ${java.util.Date(refreshed.currentCycleStartDate)}"
        } catch (e: Exception) {
            "Error stats: ${e.message}"
        }
    }

    suspend fun updateSubscriptionType(userId: String, type: String) {
        try {
            // 🛡️ FOUNDER PROTECTION: Never downgrade a founder
            val snapshot = usersCollection.document(userId).get().await()
            val isFounder = snapshot.getBoolean("isFounder") == true
            if (isFounder && type != "FOUNDER") {
                android.util.Log.w("SubscriptionRepo", "Blocked attempt to change Founder $userId to $type")
                return
            }
            usersCollection.document(userId).update("subscriptionType", type).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Records an ad watch.
     * Logic: 2 Ads = 1 Unlock.
     * @return Pair<EventsUnlocked, ProgressToNext>
     */
    suspend fun recordAdWatch(userId: String): Result<Pair<Int, Int>> {
        return try {
            val userRef = usersCollection.document(userId)
            val result = firestore.runTransaction { tx ->
                val snapshot = tx.get(userRef)
                val currentProgress = snapshot.getLong("adsWatchedProgress")?.toInt() ?: 0
                val currentUnlocked = snapshot.getLong("adEventsUnlocked")?.toInt() ?: 0
                
                // 🛑 MAX CAP: 1 Unlock per month
                if (currentUnlocked >= 1) {
                    return@runTransaction Pair(currentUnlocked, currentProgress) // No changing
                }

                var newProgress = currentProgress + 1
                var newUnlocked = currentUnlocked
                
                if (newProgress >= 2) {
                    newProgress = 0
                    newUnlocked += 1
                }
                
                tx.update(userRef, mapOf(
                    "adsWatchedProgress" to newProgress,
                    "adEventsUnlocked" to newUnlocked
                ))
                
                Pair(newUnlocked, newProgress)
            }.await()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
