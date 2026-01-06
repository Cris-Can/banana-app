package com.eventos.banana.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String): Result<Unit> {
        return try {
            firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
        fun currentUserEmail(): String? {
            return firebaseAuth.currentUser?.email
        }

    }

    fun currentUid(): String? {
        return firebaseAuth.currentUser?.uid
    }

    fun logout() {
        firebaseAuth.signOut()
    }
    fun currentUserEmail(): String? {
        return firebaseAuth.currentUser?.email
    }

}
