package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.data.remote.model.UserProfileDto
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class UserCoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val users = firestore.collection("users")

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            val dto = UserProfileDto.fromDomain(profile)
            users.document(profile.uid).set(dto).await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserCoreRepository", "Error saving profile", e)
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(uid: String, forceRefresh: Boolean = false): UserProfile? {
        return try {
            val source = if (forceRefresh) Source.SERVER else Source.DEFAULT
            val snapshot = users.document(uid).get(source).await()
            snapshot.toObject(UserProfileDto::class.java)?.toDomain()?.copy(uid = uid)
        } catch (e: Exception) {
            android.util.Log.e("UserCoreRepository", "Error getting profile for $uid", e)
            null
        }
    }

    suspend fun getUsers(uids: List<String>): List<UserProfile> {
        if (uids.isEmpty()) return emptyList()
        val chunks = uids.distinct().chunked(10)
        val allUsers = mutableListOf<UserProfile>()
        try {
            for (chunk in chunks) {
                val snapshot = users
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get()
                    .await()
                val chunkUsers = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserProfileDto::class.java)?.toDomain()?.copy(uid = doc.id)
                }
                allUsers.addAll(chunkUsers)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserCoreRepository", "Error batch fetching users", e)
        }
        return allUsers
    }

    suspend fun updateNickname(uid: String, nickname: String) {
        users.document(uid).update("nickname", nickname).await()
    }

    fun observeUserProfile(uid: String): Flow<UserProfile> = callbackFlow {
        val registration = users.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                close(error)
                return@addSnapshotListener
            }
            val profile = snapshot.toObject(UserProfileDto::class.java)?.toDomain()?.copy(uid = uid)
            if (profile != null) trySend(profile)
        }
        awaitClose { registration.remove() }
    }

    suspend fun updateLocation(uid: String, region: String, commune: String, country: String? = null, lat: Double? = null, lng: Double? = null, geohash: String? = null) {
        val updates = mutableMapOf<String, Any>("region" to region, "commune" to commune)
        if (country != null) updates["country"] = country
        if (lat != null) updates["latitude"] = lat
        if (lng != null) updates["longitude"] = lng
        if (geohash != null) updates["geohash"] = geohash
        users.document(uid).update(updates).await()
    }

    suspend fun updateSearchRadius(uid: String, radiusKm: Int) {
        users.document(uid).update("searchRadiusKm", radiusKm).await()
    }

    suspend fun updateVerificationStatus(uid: String, verified: Boolean) {
        users.document(uid).set(mapOf("isVerified" to verified), SetOptions.merge()).await()
    }

    suspend fun updateSocialProfile(uid: String, aboutMe: String, interests: List<String>) {
        users.document(uid).update(mapOf("aboutMe" to aboutMe, "interests" to interests)).await()
    }

    suspend fun updateAppTheme(uid: String, theme: String) {
        users.document(uid).update("appTheme", theme).await()
    }

    suspend fun searchUsers(query: String): List<UserProfile> {
        if (query.isBlank()) return emptyList()
        return try {
            val snapshot = users.orderBy("nickname").startAt(query).endAt(query + "\uf8ff").limit(20).get().await()
            snapshot.documents.mapNotNull { doc -> doc.toObject(UserProfileDto::class.java)?.toDomain()?.copy(uid = doc.id) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUsersByRegion(region: String, excludeUid: String): List<UserProfile> {
        return try {
            val snapshot = users.whereEqualTo("region", region).limit(50).get().await()
            snapshot.documents.mapNotNull { doc -> doc.toObject(UserProfileDto::class.java)?.toDomain()?.copy(uid = doc.id) }.filter { it.uid != excludeUid }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUsersByCommune(commune: String, excludeUid: String): List<UserProfile> {
        return try {
            val snapshot = users.whereEqualTo("commune", commune).limit(50).get().await()
            snapshot.documents.mapNotNull { doc -> doc.toObject(UserProfileDto::class.java)?.toDomain()?.copy(uid = doc.id) }.filter { it.uid != excludeUid }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Búsqueda por proximidad usando Geohash (Escalable y Económico)
     */
    suspend fun getUsersByProximity(geohash: String, excludeUid: String, limit: Int = 30): List<UserProfile> {
        if (geohash.isBlank()) return emptyList()
        return try {
            // Un geohash de 4 caracteres cubre aprox 20km. 
            // Usamos el prefijo para una consulta de rango eficiente.
            val prefix = geohash.take(4) 
            val snapshot = users
                .orderBy("geohash")
                .startAt(prefix)
                .endAt(prefix + "\uf8ff")
                .limit(limit.toLong() * 2) // Pedimos más para filtrar el propio usuario y amigos después
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc -> 
                doc.toObject(UserProfileDto::class.java)?.toDomain()?.copy(uid = doc.id) 
            }.filter { it.uid != excludeUid }
        } catch (e: Exception) {
            android.util.Log.e("UserCoreRepository", "Error en búsqueda por proximidad", e)
            emptyList()
        }
    }

    /**
     * Observa la colección friendships (V2) para obtener la lista real de IDs de amigos.
     */
    fun observeActualFriendships(uid: String): Flow<List<String>> = callbackFlow {
        val registration = firestore.collection("friendships")
            .whereEqualTo("ownerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("UserCoreRepository", "Error observando friendships V2", error)
                    return@addSnapshotListener
                }
                val friendIds = snapshot?.documents?.mapNotNull { it.getString("friendId") } ?: emptyList()
                trySend(friendIds)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Observa la colección friend_requests (V2) para obtener solicitudes recibidas pendientes.
     */
    fun observeActualFriendRequestsReceived(uid: String): Flow<List<String>> = callbackFlow {
        val registration = firestore.collection("friend_requests")
            .whereEqualTo("receiverId", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("UserCoreRepository", "Error observando requests V2", error)
                    return@addSnapshotListener
                }
                val requesterIds = snapshot?.documents?.mapNotNull { it.getString("senderId") } ?: emptyList()
                trySend(requesterIds)
            }
        awaitClose { registration.remove() }
    }
}
