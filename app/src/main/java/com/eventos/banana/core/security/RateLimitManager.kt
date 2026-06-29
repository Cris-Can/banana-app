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
        const val ACTION_RATING = "rating"

        // Actions that must fail closed if the rate limit check itself fails
        private val FAIL_CLOSED_ACTIONS = setOf(
            ACTION_LOGIN,
            ACTION_REGISTER,
            "redeemCode",
            "redeemCodeIP",
            "sendMessage",
            "purchaseValidation"
        )
        
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
                    errorMessage = "Demasiados intentos. Por favor espera antes de volver a intentarlo.",
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
            Timber.e(e, "$TAG: Error al verificar rate limit para la acción: $action")
            if (action in FAIL_CLOSED_ACTIONS) {
                // Fail closed - denegar acciones sensibles si la verificación falla
                RateLimitResult(
                    success = false,
                    errorMessage = "Servicio no disponible. Intenta de nuevo.",
                    remaining = null,
                    resetAt = null
                )
            } else {
                // Fail open para acciones no sensibles
                RateLimitResult(success = true, remaining = null, resetAt = null)
            }
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
                errorMessage = "Límite de intentos superado",
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
     * Validate password strength locally.
     */
    fun validatePasswordStrength(password: String): PasswordValidationResult {
        val minLength = 8
        val maxLength = 128
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasNumbers = password.any { it.isDigit() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }

        val errors = mutableListOf<String>()

        if (password.length < minLength) {
            errors.add("Password must be at least $minLength characters long")
        }
        if (password.length > maxLength) {
            errors.add("Password must not exceed $maxLength characters")
        }
        if (!hasUpperCase) {
            errors.add("Password must contain at least one uppercase letter")
        }
        if (!hasLowerCase) {
            errors.add("Password must contain at least one lowercase letter")
        }
        if (!hasNumbers) {
            errors.add("Password must contain at least one number")
        }
        if (!hasSpecialChar) {
            errors.add("Password must contain at least one special character")
        }

        val commonPatterns = listOf(
            Regex("(?i)^(password|123456|12345678|qwerty|abc123)"),
            Regex("(.)\\1{2,}") // Same character repeated 3+ times
        )

        for (pattern in commonPatterns) {
            if (pattern.containsMatchIn(password)) {
                errors.add("Password contains a common or weak pattern")
                break
            }
        }

        val isValid = errors.isEmpty()
        val conditionsMet = listOf(hasUpperCase, hasLowerCase, hasNumbers, hasSpecialChar, password.length >= 12).count { it }
        val strength = if (isValid) Math.min(5, conditionsMet) else 0

        return PasswordValidationResult(
            isValid = isValid,
            strength = strength,
            errors = errors
        )
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
                message = "Error al registrar la vista de perfil"
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