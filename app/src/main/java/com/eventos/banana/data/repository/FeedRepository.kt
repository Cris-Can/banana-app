package com.eventos.banana.data.repository

import com.eventos.banana.data.remote.storage.FirebaseStorageDataSource
import com.eventos.banana.util.ImageCompressor
import com.eventos.banana.domain.model.FeedPost
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

import javax.inject.Inject

class FeedRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storageDataSource: FirebaseStorageDataSource
) {

    fun getPosts(eventId: String): Flow<List<FeedPost>> = callbackFlow {
        android.util.Log.d("FeedRepository", "📡 Setting up listener for eventId: $eventId")
        
        val listener = firestore.collection("events")
            .document(eventId)
            .collection("feed")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FeedRepository", "❌ Listener error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                android.util.Log.d("FeedRepository", "📥 Snapshot received. Size: ${snapshot?.size() ?: 0}")
                
                try {
                    val posts = snapshot?.toObjects(FeedPost::class.java) ?: emptyList()
                    android.util.Log.d("FeedRepository", "✅ Deserialized ${posts.size} posts successfully")
                    trySend(posts)
                } catch (e: Exception) {
                    android.util.Log.e("FeedRepository", "💥 Deserialization error", e)
                    trySend(emptyList())
                }
            }

        awaitClose { 
            android.util.Log.d("FeedRepository", "🔌 Removing listener for eventId: $eventId")
            listener.remove() 
        }
    }

    /**
     * Creates a new post in the feed.
     * Note: imageBytes should be compressed at the caller level (e.g. using ImageCompressor)
     * to avoid uploading raw large files (e.g. 1024x1024, quality 80).
     */
    suspend fun createPost(post: FeedPost, imageBytes: ByteArray?): Result<Unit> {
        return try {
            var imageUrl: String? = null
            val postId = UUID.randomUUID().toString() 
            val imagePath = imageBytes?.let { "events_feed/${post.eventId}/$postId.jpg" }

            // 1. Upload Image (if exists) using optimized DataSource
            if (imageBytes != null && imagePath != null) {
                val uploadResult = storageDataSource.uploadFile(imagePath, imageBytes)
                if (uploadResult.isSuccess) {
                    imageUrl = uploadResult.getOrNull().also {
                        if (it == null) android.util.Log.w("FeedRepository", "Upload succeeded but returned null URL")
                    }
                } else {
                    val exception = uploadResult.exceptionOrNull() ?: Exception("Upload failed")
                    android.util.Log.e("FeedRepository", "Upload failed", exception)
                    return Result.failure(exception)
                }
            }

            // 2. Save Element
            val docRef = firestore.collection("events")
                .document(post.eventId)
                .collection("feed")
                .document()

            val postData = hashMapOf(
                "id" to docRef.id,
                "eventId" to post.eventId,
                "userId" to post.userId,
                "userNickname" to post.userNickname,
                "content" to post.content,
                "imageUrl" to imageUrl,
                "isUserVerified" to post.isUserVerified,
                "isUserIdentityVerified" to post.isUserIdentityVerified,
                "replyToId" to post.replyToId,
                "replyToNickname" to post.replyToNickname,
                "replyToContent" to post.replyToContent,
                "replyToUserId" to post.replyToUserId,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            docRef.set(postData).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
