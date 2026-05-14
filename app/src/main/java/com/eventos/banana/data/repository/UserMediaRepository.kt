package com.eventos.banana.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class UserMediaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storageDataSource: com.eventos.banana.data.remote.storage.FirebaseStorageDataSource
) {
    private val users = firestore.collection("users")

    suspend fun uploadProfilePhoto(uid: String, imageBytes: ByteArray?, isProfilePicture: Boolean, isCoverPhoto: Boolean = false): Result<Unit> {
        return try {
            if (imageBytes == null || imageBytes.isEmpty()) {
                throw Exception("No se proporcionaron datos de imagen")
            }

            val imagePath = "users/$uid/photos/${UUID.randomUUID()}.jpg"
            
            val uploadResult = storageDataSource.uploadFile(imagePath, imageBytes)
            val downloadUrl = uploadResult.getOrThrow()

            android.util.Log.d("UserMediaRepository", "Photo uploaded OK: $downloadUrl")

            // Update Firestore with the URL
            when {
                isProfilePicture -> users.document(uid).update("profilePictureUrl", downloadUrl).await()
                isCoverPhoto -> users.document(uid).update("coverPhotoUrl", downloadUrl).await()
                else -> users.document(uid).update("photos", FieldValue.arrayUnion(downloadUrl)).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserMediaRepository", "❌ Photo upload failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deletePhoto(uid: String, photoUrl: String) {
        users.document(uid).update(
            "photos",
            FieldValue.arrayRemove(photoUrl)
        ).await()
    }
}
