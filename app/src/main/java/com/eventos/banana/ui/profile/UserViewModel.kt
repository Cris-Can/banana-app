package com.eventos.banana.ui.profile

import androidx.lifecycle.ViewModel
import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import com.eventos.banana.data.repository.UserRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

@HiltViewModel
class UserViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    val userRepository: UserRepository
) : ViewModel() {
    private val usersCollection = db.collection("users")

    suspend fun getBlockedUsersProfiles(uid: String): List<UserProfile> = 
        userRepository.getBlockedUsersProfiles(uid)

    suspend fun unblockUser(currentUid: String, targetUid: String) = 
        userRepository.unblockUser(currentUid, targetUid)

    suspend fun getPendingReports(): List<Map<String, Any>> = 
        userRepository.getPendingReports()

    suspend fun resolveReport(reportId: String, action: String) = 
        userRepository.resolveReport(reportId, action)

    suspend fun banUser(uid: String) = 
        userRepository.banUser(uid)

    suspend fun generateFounderCode(uid: String, durationDays: Int? = null): Result<String> =
        userRepository.generateFounderCode(uid, durationDays)

    suspend fun searchUsers(query: String, currentUserId: String): List<UserProfile> {
        return try {
            val results = mutableSetOf<UserProfile>()
            
            // 1. Search as typed
            val snapshot1 = usersCollection
                .whereGreaterThanOrEqualTo("nickname", query)
                .whereLessThanOrEqualTo("nickname", query + "\uf8ff")
                .limit(10)
                .get()
                .await()
            results.addAll(snapshot1.toObjects(UserProfile::class.java))

            // 2. Search capitalized (e.g. "cris" -> "Cris") if query is lowercase
            if (query.isNotEmpty() && query[0].isLowerCase()) {
                val capitalized = query.replaceFirstChar { it.uppercase() }
                val snapshot2 = usersCollection
                    .whereGreaterThanOrEqualTo("nickname", capitalized)
                    .whereLessThanOrEqualTo("nickname", capitalized + "\uf8ff")
                    .limit(10)
                    .get()
                    .await()
                results.addAll(snapshot2.toObjects(UserProfile::class.java))
            }
            
            // 3. Search lowercase (e.g. "Cris" -> "cris") if query is uppercase
            if (query.isNotEmpty() && query[0].isUpperCase()) {
                val lowercase = query.lowercase()
                val snapshot3 = usersCollection
                    .whereGreaterThanOrEqualTo("nickname", lowercase)
                    .whereLessThanOrEqualTo("nickname", lowercase + "\uf8ff")
                    .limit(10)
                    .get()
                    .await()
                results.addAll(snapshot3.toObjects(UserProfile::class.java))
            }

            results.filter { it.uid != currentUserId }.toList()
        } catch (e: Exception) {
            Timber.e(e, "Search error")
            emptyList()
        }
    }

    suspend fun cleanupUsersDatabase(): Result<String> = 
        userRepository.cleanupUsersDatabase()

    suspend fun getPendingIdentityVerifications(): List<UserProfile> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("identityVerificationStatus", "pending")
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(com.eventos.banana.data.remote.model.UserProfileDto::class.java)?.toDomain()?.copy(uid = doc.id)
            }
        } catch (e: Exception) {
            Timber.e(e, "Search error")
            emptyList()
        }
    }

    suspend fun approveIdentityVerification(uid: String): Result<Unit> {
        return try {
            usersCollection.document(uid).update(
                mapOf(
                    "identityVerified" to true,
                    "identityVerificationStatus" to "approved",
                    "identityVerifiedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Notificar al usuario que su verificación fue aprobada
            val adminUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val notificationData = mapOf(
                "userId" to uid,
                "fromUserId" to adminUid,
                "title" to "Identidad Aprobada",
                "message" to "Tu verificación de identidad ha sido aprobada. Ya puedes crear y ver eventos +18.",
                "type" to com.eventos.banana.domain.model.NotificationType.GENERIC.name,
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("notifications").add(notificationData).await()

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserViewModel", "Error approving verification for $uid", e)
            Result.failure(e)
        }
    }

    suspend fun rejectIdentityVerification(uid: String): Result<Unit> {
        return try {
            usersCollection.document(uid).update(
                mapOf(
                    "identityVerificationStatus" to "rejected"
                )
            ).await()

            // Notificar al usuario que su verificación fue rechazada
            val adminUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val notificationData = mapOf(
                "userId" to uid,
                "fromUserId" to adminUid,
                "title" to "Identidad Rechazada",
                "message" to "Tu verificación de identidad ha sido rechazada. Si crees que es un error, contacta al soporte.",
                "type" to com.eventos.banana.domain.model.NotificationType.GENERIC.name,
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("notifications").add(notificationData).await()

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserViewModel", "Error rejecting verification for $uid", e)
            Result.failure(e)
        }
    }
}
