package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class UserRepository {

    private val users = FirebaseFirestore
        .getInstance()
        .collection("users")

    // ---------- CREATE PROFILE ----------
    suspend fun createUserProfile(profile: UserProfile) {
        users
            .document(profile.uid)
            .set(profile)
            .await()
    }

    // ---------- GET PROFILE (CACHE FIRST) ----------
    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            // 1️⃣ Cache primero
            val cachedSnapshot = users
                .document(uid)
                .get(Source.CACHE)
                .await()

            if (cachedSnapshot.exists()) {
                cachedSnapshot.toObject(UserProfile::class.java)
            } else {
                // 2️⃣ Red si no está en cache
                val serverSnapshot = users
                    .document(uid)
                    .get(Source.SERVER)
                    .await()

                serverSnapshot.toObject(UserProfile::class.java)
            }

        } catch (e: Exception) {
            null
        }
    }
}
