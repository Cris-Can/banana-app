package com.eventos.banana.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserAdminRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val users = firestore.collection("users")

    suspend fun reportUser(reporterUid: String, reportedUid: String, reason: String): Result<Unit> {
        return try {
            val report = mapOf(
                "reporterId" to reporterUid,
                "reportedId" to reportedUid,
                "reason" to reason,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "status" to "PENDING"
            )
            firestore.collection("reports").add(report).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPendingReports(): List<Map<String, Any>> {
        return try {
            val snapshot = firestore.collection("reports")
                .whereEqualTo("status", "PENDING")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.map { doc ->
                doc.data?.plus("id" to doc.id) ?: emptyMap()
            }
        } catch (e: Exception) {
            android.util.Log.e("UserAdminRepository", "Error getting reports", e)
            emptyList()
        }
    }

    suspend fun resolveReport(reportId: String, action: String): Result<Unit> {
        return try {
            firestore.collection("reports").document(reportId)
                .update("status", action) // "RESOLVED" or "IGNORED"
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun banUser(uid: String): Result<Unit> {
        return try {
            users.document(uid).update("banned", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun unbanUser(uid: String): Result<Unit> {
        return try {
            users.document(uid).update("banned", false).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateFounderCode(createdByUid: String, durationDays: Int? = null): Result<String> {
        return try {
            val code = "FOUNDER-" + java.util.UUID.randomUUID().toString().substring(0, 8).uppercase()
            val docData = mutableMapOf(
                "isUsed" to false,
                "type" to "FOUNDER",
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "createdBy" to createdByUid
            )
            
            if (durationDays != null) {
                docData["durationDays"] = durationDays
            }
            
            firestore.collection("invitation_codes").document(code).set(docData).await()
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cleanupUsersDatabase(): Result<String> {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("cleanupUsersDatabase")
                .call(mapOf("force" to true)) // Envío de datos ficticios para evitar error de shell/v2
                .await()
            
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any>
            val message = data?.get("message")?.toString() ?: "Limpieza completada."
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
