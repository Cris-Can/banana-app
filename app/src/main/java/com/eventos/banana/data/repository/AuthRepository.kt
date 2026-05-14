package com.eventos.banana.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.eventos.banana.core.security.RateLimitManager
import kotlinx.coroutines.tasks.await
import timber.log.Timber

import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val rateLimitManager: RateLimitManager
) {

    companion object {
        private const val TAG = "AuthRepository"
    }

    fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    /**
     * Login with rate limiting protection
     */
    suspend fun login(email: String, password: String): Result<Unit> {
        // 1. Check rate limit first
        val rateLimitResult = rateLimitManager.checkRateLimit(RateLimitManager.ACTION_LOGIN)
        if (!rateLimitResult.success) {
            Timber.w(TAG, "Login rate limit exceeded for action")
            return Result.failure(
                Exception("Too many login attempts. Please wait ${rateLimitResult.timeUntilReset} and try again.")
            )
        }

        // 2. Proceed with login
        return try {
            firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, TAG, "Login failed")
            Result.failure(e)
        }
    }

    /**
     * Register with rate limiting and password validation
     */
    suspend fun register(email: String, password: String): Result<Unit> {
        // 1. Check rate limit first
        val rateLimitResult = rateLimitManager.checkRateLimit(RateLimitManager.ACTION_REGISTER)
        if (!rateLimitResult.success) {
            Timber.w(TAG, "Register rate limit exceeded")
            return Result.failure(
                Exception("Too many registration attempts. Please wait ${rateLimitResult.timeUntilReset} and try again.")
            )
        }

        // 2. Validate password strength server-side
        val passwordValidation = rateLimitManager.validatePasswordStrength(password)
        if (!passwordValidation.isValid) {
            Timber.w(TAG, "Password validation failed: ${passwordValidation.errors}")
            return Result.failure(
                Exception("Password does not meet security requirements: ${passwordValidation.errors.joinToString(", ")}")
            )
        }

        // 3. Proceed with registration
        return try {
            firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, TAG, "Registration failed")
            Result.failure(e)
        }
    }

    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            firebaseAuth.currentUser?.sendEmailVerification()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reloadUser(): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
            user?.reload()?.await()
            // 🔄 Force token refresh to update custom claims (like email_verified) for Firestore rules
            user?.getIdToken(true)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isEmailVerified(): Boolean {
        return firebaseAuth.currentUser?.isEmailVerified == true
    }

    fun currentUid(): String? {
        return firebaseAuth.currentUser?.uid
    }

    fun getCurrentUser() = firebaseAuth.currentUser

    fun currentUserEmail(): String? {
        return firebaseAuth.currentUser?.email
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        firebaseAuth.signOut()
    }
    
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            firebaseAuth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
