package com.eventos.banana.core.security

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import com.google.firebase.functions.ktx.functions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rate Limit Manager for Android Client
 * 
 * This class manages rate limiting on the client side by:
 * 1. Checking rate limits before performing sensitive actions
 * 2. Caching rate limit status to reduce network calls
 * 3. Providing user-friendly error messages when limits are exceeded
 */
@Singleton
class RateLimitManager @Inject constructor(
    private val firebaseFunctions: FirebaseFunctions = Firebase.functions
) {
    
    companion object {
        private const val TAG = "RateLimitManager"
        
        // Rate limit actions
        const val ACTION_LOGIN = "login"
        const val ACTION_REGISTER = "register"
        const val ACTION_PROFILE_VIEW = "profileView"
        const val ACTION_SEND_MESSAGE = "sendMessage"
        const val ACTION_EVENT_CREATION = "eventCreation"
        
        // Cache duration for rate limit status (5 minutes)
        internal const val CACHE_DURATION_MS = 5 * 60 * 1000L
    }
    
    // Cache for rate limit status
    private val rateLimitCache = mutableMapOf<String, RateLimitStatus>()
    
    /**
     * Check if an action is rate limited.
     * Returns true if the action can proceed, false if rate limited.
     */
    suspend fun checkRateLimit(action: String): RateLimitResult {
        val cacheKey = "${getCurrentUserId()}_$action"
        
        // Check cache first
        val cachedStatus = rateLimitCache[cacheKey]
        if (cachedStatus != null && !cachedStatus.isExpired()) {
            if (!cachedStatus.canProceed) {
                return RateLimitResult(
                    success = false,
                    errorMessage = "Too many attempts. Please wait before trying again.",
                    remaining = 0,
                    resetAt = cachedStatus.resetAt
                )
            }
            return RateLimitResult(
                success = true,
                remaining = cachedStatus.remaining,
                resetAt = cachedStatus.resetAt
            )
        }
        
        // Call server to check rate limit
        return try {
            val result = checkRateLimitOnServer(action)
            
            // Update cache
            if (result.success) {
                rateLimitCache[cacheKey] = RateLimitStatus(
                    canProceed = true,
                    remaining = result.remaining ?: 0,
                    resetAt = result.resetAt ?: (System.currentTimeMillis() + 3600000),
                    cachedAt = System.currentTimeMillis()
                )
            } else {
                rateLimitCache[cacheKey] = RateLimitStatus(
                    canProceed = false,
                    remaining = 0,
                    resetAt = result.resetAt ?: (System.currentTimeMillis() + 3600000),
                    cachedAt = System.currentTimeMillis()
                )
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to check rate limit for action: $action")
            // Fail open - allow the action if we can't check
            RateLimitResult(success = true, remaining = null, resetAt = null)
        }
    }
    
    /**
     * Call the server-side rate limit check function.
     */
    private suspend fun checkRateLimitOnServer(action: String): RateLimitResult {
        val data = hashMapOf("action" to action)
        
        val result = firebaseFunctions
            .getHttpsCallable("checkRateLimitGuard")
            .call(data)
            .await()
        
        val responseData = result.data as? Map<*, *>
        
        return if (responseData?.get("success") == true) {
            RateLimitResult(
                success = true,
                remaining = (responseData["remaining"] as? Number)?.toInt(),
                resetAt = (responseData["resetAt"] as? Number)?.toLong()
            )
        } else {
            RateLimitResult(
                success = false,
                errorMessage = "Rate limit exceeded",
                remaining = 0,
                resetAt = (responseData?.get("resetAt") as? Number)?.toLong()
            )
        }
    }
    
    /**
     * Clear the rate limit cache for a specific action.
     */
    fun clearCache(action: String) {
        val cacheKey = "${getCurrentUserId()}_$action"
        rateLimitCache.remove(cacheKey)
    }
    
    /**
     * Clear all rate limit cache entries.
     */
    fun clearAllCache() {
        rateLimitCache.clear()
    }
    
    /**
     * Get current user ID for rate limit tracking.
     * Returns "anonymous" for unauthenticated users.
     */
    private fun getCurrentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
    }
    
    /**
     * Validate password strength using server-side validation.
     */
    suspend fun validatePasswordStrength(password: String): PasswordValidationResult {
        return try {
            val data = hashMapOf("password" to password)
            
            val result = firebaseFunctions
                .getHttpsCallable("validatePasswordStrength")
                .call(data)
                .await()
            
            val responseData = result.data as? Map<*, *>
            
            PasswordValidationResult(
                isValid = responseData?.get("isValid") as? Boolean ?: false,
                strength = (responseData?.get("strength") as? Number)?.toInt() ?: 0,
                errors = (responseData?.get("errors") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to validate password strength")
            PasswordValidationResult(
                isValid = false,
                strength = 0,
                errors = listOf("Failed to validate password. Please try again.")
            )
        }
    }
    
    /**
     * Record a profile view with rate limiting.
     */
    suspend fun recordProfileView(targetUserId: String): ProfileViewResult {
        return try {
            val data = hashMapOf("targetUid" to targetUserId)
            
            val result = firebaseFunctions
                .getHttpsCallable("recordProfileView")
                .call(data)
                .await()
            
            val responseData = result.data as? Map<*, *>
            
            ProfileViewResult(
                success = responseData?.get("success") as? Boolean ?: false,
                remaining = (responseData?.get("remaining") as? Number)?.toInt(),
                message = responseData?.get("message") as? String
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to record profile view")
            ProfileViewResult(
                success = false,
                remaining = null,
                message = "Failed to record profile view"
            )
        }
    }
}

/**
 * Result of a rate limit check.
 */
data class RateLimitResult(
    val success: Boolean,
    val remaining: Int? = null,
    val resetAt: Long? = null,
    val errorMessage: String? = null
) {
    val timeUntilReset: String
        get() {
            if (resetAt == null) return "unknown time"
            val millis = resetAt - System.currentTimeMillis()
            if (millis <= 0) return "now"
            
            val minutes = millis / 60000
            return when {
                minutes < 1 -> "a few seconds"
                minutes < 60 -> "$minutes minute(s)"
                else -> "${minutes / 60} hour(s)"
            }
        }
}

/**
 * Cached rate limit status.
 */
private data class RateLimitStatus(
    val canProceed: Boolean,
    val remaining: Int,
    val resetAt: Long,
    val cachedAt: Long
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - cachedAt > RateLimitManager.CACHE_DURATION_MS
    }
}

/**
 * Result of password validation.
 */
data class PasswordValidationResult(
    val isValid: Boolean,
    val strength: Int,
    val errors: List<String>
)

/**
 * Result of profile view recording.
 */
data class ProfileViewResult(
    val success: Boolean,
    val remaining: Int? = null,
    val message: String? = null
)