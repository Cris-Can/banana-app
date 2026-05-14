package com.eventos.banana.core.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * 🍌 Banana App - Centralized Logging System
 * 
 * Provides consistent logging across the application with:
 * - Timber for debug logs
 * - Firebase Crashlytics for critical errors
 * - Structured log tags for easy filtering
 * 
 * Usage:
 * ```
 * AppLogger.d("MyTag", "Debug message")
 * AppLogger.e("MyTag", "Error message", exception)
 * AppLogger.logBillingEvent("purchase_started", mapOf("productId" to "banana_plus"))
 * ```
 */
object AppLogger {

    // ============================================
    // 📝 LOG TAGS (Standardized)
    // ============================================
    
    object Tags {
        const val AUTH = "Banana_Auth"
        const val BILLING = "Banana_Billing"
        const val NOTIFICATIONS = "Banana_Notifications"
        const val MESSAGING = "Banana_Messaging"
        const val EVENTS = "Banana_Events"
        const val USERS = "Banana_Users"
        const val RATINGS = "Banana_Ratings"
        const val LOCATION = "Banana_Location"
        const val AUDIO = "Banana_Audio"
        const val NETWORK = "Banana_Network"
        const val DATABASE = "Banana_Database"
        const val SECURITY = "Banana_Security"
        const val PERFORMANCE = "Banana_Performance"
    }

    // ============================================
    // 🌲 TIMBER LOGGING
    // ============================================
    
    /**
     * Log verbose message
     */
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).v(throwable, message)
    }
    
    /**
     * Log debug message
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).d(throwable, message)
    }
    
    /**
     * Log info message
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).i(throwable, message)
    }
    
    /**
     * Log warning message
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).w(throwable, message)
    }
    
    /**
     * Log error message
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).e(throwable, message)
    }
    
    /**
     * Log wtf (What a Terrible Failure) message
     */
    fun wtf(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).wtf(throwable, message)
    }

    // ============================================
    // 🔥 CRASHLYTICS INTEGRATION
    // ============================================
    
    /**
     * Record a non-fatal exception to Crashlytics
     */
    fun recordException(exception: Throwable, tag: String = Tags.SECURITY) {
        FirebaseCrashlytics.getInstance().recordException(exception)
        e(tag, "Exception recorded to Crashlytics: ${exception.message}", exception)
    }
    
    /**
     * Record a custom message to Crashlytics
     */
    fun recordMessage(message: String, tag: String = Tags.SECURITY) {
        FirebaseCrashlytics.getInstance().log("[$tag] $message")
        d(tag, message)
    }
    
    /**
     * Set a custom key for Crashlytics
     */
    fun setCrashlyticsKey(key: String, value: String) {
        FirebaseCrashlytics.getInstance().setCustomKey(key, value)
    }
    
    /**
     * Set user ID for Crashlytics
     */
    fun setUserId(userId: String) {
        FirebaseCrashlytics.getInstance().setUserId(userId)
    }

    // ============================================
    // 📊 SPECIALIZED LOGGING
    // ============================================
    
    // --- AUTH LOGGING ---
    
    fun logAuthEvent(event: String, details: Map<String, Any> = emptyMap()) {
        val detailStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        d(Tags.AUTH, "Auth Event: $event - $detailStr")
        recordMessage("Auth: $event - $detailStr", Tags.AUTH)
    }
    
    fun logAuthError(event: String, error: Exception, details: Map<String, Any> = emptyMap()) {
        val detailStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        e(Tags.AUTH, "Auth Error: $event - $detailStr", error)
        recordException(error, Tags.AUTH)
    }
    
    // --- BILLING LOGGING ---
    
    fun logBillingEvent(event: String, details: Map<String, Any> = emptyMap()) {
        val detailStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        d(Tags.BILLING, "Billing Event: $event - $detailStr")
        recordMessage("Billing: $event - $detailStr", Tags.BILLING)
    }
    
    fun logBillingError(event: String, error: Exception, details: Map<String, Any> = emptyMap()) {
        val detailStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        e(Tags.BILLING, "Billing Error: $event - $detailStr", error)
        recordException(error, Tags.BILLING)
    }
    
    // --- NOTIFICATION LOGGING ---
    
    fun logNotificationEvent(event: String, details: Map<String, Any> = emptyMap()) {
        val detailStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        d(Tags.NOTIFICATIONS, "Notification Event: $event - $detailStr")
    }
    
    fun logNotificationError(event: String, error: Exception, details: Map<String, Any> = emptyMap()) {
        val detailStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        e(Tags.NOTIFICATIONS, "Notification Error: $event - $detailStr", error)
        recordException(error, Tags.NOTIFICATIONS)
    }
    
    // --- PERFORMANCE LOGGING ---
    
    /**
     * Log performance metrics
     */
    fun logPerformance(operation: String, durationMs: Long, success: Boolean = true) {
        val status = if (success) "✅" else "❌"
        d(Tags.PERFORMANCE, "$status $operation completed in ${durationMs}ms")
    }
    
    /**
     * Log memory usage
     */
    fun logMemoryUsage(usedMemoryMb: Long, totalMemoryMb: Long) {
        d(Tags.PERFORMANCE, "Memory: ${usedMemoryMb}MB / ${totalMemoryMb}MB (${(usedMemoryMb.toFloat() / totalMemoryMb * 100).toInt()}%)")
    }

    // ============================================
    // 🛡️ CRITICAL ERROR LOGGING
    // ============================================
    
    /**
     * Log a critical error that should be investigated
     */
    fun logCriticalError(context: String, error: Exception, additionalInfo: Map<String, Any> = emptyMap()) {
        val infoStr = additionalInfo.entries.joinToString(", ") { "${it.key}=${it.value}" }
        
        // Log to Timber
        e(Tags.SECURITY, "🚨 CRITICAL ERROR in $context: ${error.message}", error)
        e(Tags.SECURITY, "Additional info: $infoStr")
        
        // Set context for Crashlytics
        setCrashlyticsKey("critical_error_context", context)
        setCrashlyticsKey("critical_error_time", System.currentTimeMillis().toString())
        additionalInfo.forEach { (key, value) ->
            setCrashlyticsKey("critical_error_$key", value.toString())
        }
        
        // Record to Crashlytics
        recordException(error, Tags.SECURITY)
    }
    
    /**
     * Log a network error with retry information
     */
    fun logNetworkError(endpoint: String, error: Exception, retryCount: Int = 0) {
        e(Tags.NETWORK, "Network error on $endpoint (retry: $retryCount): ${error.message}", error)
        if (retryCount >= 3) {
            recordException(error, Tags.NETWORK)
        }
    }
    
    /**
     * Log a database error
     */
    fun logDatabaseError(operation: String, error: Exception, collection: String = "") {
        e(Tags.DATABASE, "Database error in $operation ($collection): ${error.message}", error)
        recordException(error, Tags.DATABASE)
    }
}

/**
 * Timber Tree for Firebase Crashlytics integration
 * Automatically logs errors to Crashlytics
 */
class CrashlyticsTree : Timber.Tree() {
    
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only log warnings and above to Crashlytics
        if (priority >= Log.WARN) {
            if (t != null) {
                FirebaseCrashlytics.getInstance().recordException(t)
            }
            FirebaseCrashlytics.getInstance().log("$message")
        }
    }
}