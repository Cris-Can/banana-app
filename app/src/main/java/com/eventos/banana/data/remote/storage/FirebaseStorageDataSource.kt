package com.eventos.banana.data.remote.storage

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStorageDataSource @Inject constructor() {

    private val projectID = "bananaapp-aa46e"
    private val buckets = listOf(
        "default",
        "$projectID.appspot.com",
        "$projectID.firebasestorage.app"
    )

    /**
     * Uploads a file to Firebase Storage using a resilient multi-bucket strategy.
     * @param path The destination path in the storage.
     * @param bytes The file content.
     * @return Result with the download URL on success.
     */
    suspend fun uploadFile(path: String, bytes: ByteArray): Result<String> {
        if (bytes.isEmpty()) return Result.failure(Exception("Empty file data"))

        var lastStorageEx: Exception? = null

        for (bucketName in buckets) {
            try {
                val storageInstance = if (bucketName == "default") {
                    FirebaseStorage.getInstance()
                } else {
                    FirebaseStorage.getInstance("gs://$bucketName")
                }

                val storageRef = storageInstance.reference.child(path)

                // 1. Upload bytes
                storageRef.putBytes(bytes).await()

                // 2. Get download URL with retries (Firestore indexing/propagation delay)
                var downloadUrl: String? = null
                for (i in 1..3) {
                    try {
                        delay(500L * i)
                        downloadUrl = storageRef.downloadUrl.await().toString()
                        if (downloadUrl != null) break
                    } catch (e: Exception) {
                        // Retry silently unless it's the last attempt
                        if (i == 3) lastStorageEx = e
                    }
                }

                if (downloadUrl != null) {
                    return Result.success(downloadUrl)
                }

            } catch (e: Exception) {
                lastStorageEx = e
                // If it's bucket not found error, try next one
                if (e is StorageException && e.errorCode == StorageException.ERROR_BUCKET_NOT_FOUND) {
                    continue
                } else if (e is StorageException && e.errorCode == -13010) { // Code for bucket issue often seen
                    continue
                } else {
                    // Critical error (permissions, network), stop trying buckets
                    break
                }
            }
        }

        val finalMsg = (lastStorageEx as? StorageException)?.let {
            "Storage Error (${it.errorCode}): ${it.message}"
        } ?: lastStorageEx?.message ?: "Unknown storage error"

        return Result.failure(Exception("Failed to upload file to any bucket: $finalMsg"))
    }
}
