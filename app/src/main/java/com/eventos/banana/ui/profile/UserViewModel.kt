package com.eventos.banana.ui.profile

import androidx.lifecycle.ViewModel
import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import com.eventos.banana.data.repository.UserRepository

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
            e.printStackTrace()
            emptyList()
        }
    }
}
