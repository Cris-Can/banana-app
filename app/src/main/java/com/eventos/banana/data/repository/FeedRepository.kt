package com.eventos.banana.data.repository

import android.net.Uri
import com.eventos.banana.domain.model.FeedPost
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

import javax.inject.Inject

class FeedRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {

    fun getPosts(eventId: String): Flow<List<FeedPost>> = callbackFlow {
        android.util.Log.d("FeedRepository", "📡 Setting up listener for eventId: $eventId")
        
        val listener = firestore.collection("events")
            .document(eventId)
            .collection("feed")
            .orderBy("timestamp", Query.Direction.DESCENDING)
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
                    posts.forEachIndexed { index, post ->
                        android.util.Log.d("FeedRepository", "  Post $index: id=${post.id}, content='${post.content.take(30)}', timestamp=${ post.timestamp}")
                    }
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

    suspend fun createPost(post: FeedPost, imageBytes: ByteArray?): Result<Unit> {
        return try {
            var imageUrl: String? = null

            // 1. Upload Image (if exists)
            if (imageBytes != null) {
                val ref = storage.reference.child("feed_images/${post.eventId}/${UUID.randomUUID()}.jpg")
                ref.putBytes(imageBytes).await()
                
                // Retry URL for 3 times
                for (i in 1..3) {
                    try {
                        kotlinx.coroutines.delay(500L * i)
                        val url = ref.downloadUrl.await().toString()
                        if (url.isNotBlank()) {
                            imageUrl = url
                            break
                        }
                    } catch (e: Exception) {}
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
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            docRef.set(postData).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
