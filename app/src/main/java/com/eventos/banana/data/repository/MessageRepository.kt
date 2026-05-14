package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.Conversation
import com.eventos.banana.domain.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

import javax.inject.Inject

class MessageRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val notificationRepository: com.eventos.banana.data.repository.NotificationRepository,
    private val storageDataSource: com.eventos.banana.data.remote.storage.FirebaseStorageDataSource
) {
    private val conversationsCollection = firestore.collection("conversations")
    
    // Obtener o crear conversación entre dos usuarios
    suspend fun getOrCreateConversation(
        currentUserId: String,
        otherUserId: String,
        currentUserNickname: String,
        otherUserNickname: String
    ): Result<String> {
        return try {
            val participants = listOf(currentUserId, otherUserId).sorted()
            
            // Buscar conversación existente
            val existing = conversationsCollection
                .whereArrayContains("participants", currentUserId)
                .get()
                .await()
                .documents
                .firstOrNull { doc ->
                    val convParticipants = doc.get("participants") as? List<*>
                    convParticipants?.containsAll(participants) == true
                }
            
            if (existing != null) {
                // 🆕 BUG FIX: Update nicknames if they were generic/placeholder
                val existingNicknames = existing.get("participantNicknames") as? Map<String, String> ?: emptyMap()
                val needsUpdate = existingNicknames.any { (id, nick) -> 
                    nick == "Usuario" || nick == "Alguien" || nick.isBlank() 
                }
                
                if (needsUpdate) {
                    val updatedNicknames = existingNicknames.toMutableMap()
                    if (currentUserId in updatedNicknames && (updatedNicknames[currentUserId] == "Usuario" || updatedNicknames[currentUserId].isNullOrBlank()) && currentUserNickname != "Usuario") {
                        updatedNicknames[currentUserId] = currentUserNickname
                    }
                    if (otherUserId in updatedNicknames && (updatedNicknames[otherUserId] == "Usuario" || updatedNicknames[otherUserId].isNullOrBlank()) && otherUserNickname != "Usuario") {
                        updatedNicknames[otherUserId] = otherUserNickname
                    }
                    
                    if (updatedNicknames != existingNicknames) {
                        conversationsCollection.document(existing.id).update("participantNicknames", updatedNicknames).await()
                        android.util.Log.d("MessageRepository", "Updated generic nicknames for conversation ${existing.id}")
                    }
                }
                
                Result.success(existing.id)
            } else {
                // Crear nueva conversación
                val conversation = Conversation(
                    participants = participants,
                    participantNicknames = mapOf(
                        currentUserId to currentUserNickname,
                        otherUserId to otherUserNickname
                    ),
                    unreadCount = mapOf(currentUserId to 0, otherUserId to 0)
                )
                
                val docRef = conversationsCollection.document()
                conversationsCollection.document(docRef.id)
                    .set(conversation.copy(id = docRef.id))
                    .await()
                
                Result.success(docRef.id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Observar conversaciones del usuario
    fun observeConversations(userId: String): Flow<List<Conversation>> = callbackFlow {
        val listener = conversationsCollection
            .whereArrayContains("participants", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MessageRepository", "Error observing conversations: ${error.message}", error)
                    // Don't crash — emit empty list and keep listening
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val conversations = snapshot?.toObjects(Conversation::class.java) ?: emptyList()
                trySend(conversations)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Enviar mensaje
    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        content: String = "",
        audioUrl: String? = null,
        audioDurationMs: Int? = null,
        replyToId: String? = null
    ): Result<Unit> {
        return try {
            val message = Message(
                conversationId = conversationId,
                senderId = senderId,
                content = content,
                audioUrl = audioUrl,
                audioDurationMs = audioDurationMs,
                replyToId = replyToId
            )
            
            val docRef = conversationsCollection.document(conversationId)
                .collection("messages")
                .document()
            
            docRef.set(message.copy(id = docRef.id)).await()
            
            // 📩 Update last message and increment unread count
            var updateFailed = false
            try {
                // Fetch to identify recipient for both unread count and notification
                val conversationSnapshot = conversationsCollection.document(conversationId).get().await()
                val participants = conversationSnapshot.get("participants") as? List<String>
                val recipientId = participants?.firstOrNull { it != senderId }
                
                val displayContent = if (audioUrl != null) "🎤 Mensaje de audio" else content

                val updates = mutableMapOf<String, Any>(
                    "lastMessage" to displayContent,
                    "lastMessageSenderId" to senderId,
                    "lastMessageTimestamp" to System.currentTimeMillis()
                )
                
                if (recipientId != null) {
                    updates["unreadCount.$recipientId"] = com.google.firebase.firestore.FieldValue.increment(1)
                }

                conversationsCollection.document(conversationId).update(updates).await()

                // 🔔 Send Notification to Recipient
                if (recipientId != null) {
                    val notifRepo = notificationRepository
                    val senderNickname = (conversationSnapshot.get("participantNicknames") as? Map<String, String>)?.get(senderId) ?: "Alguien"
                    
                    notifRepo.sendNotification(
                        com.eventos.banana.domain.model.AppNotification(
                            userId = recipientId,
                            title = "Nuevo mensaje de $senderNickname",
                            message = displayContent,
                            eventId = conversationId,
                            conversationId = conversationId,
                            type = com.eventos.banana.domain.model.NotificationType.NEW_MESSAGE
                        )
                    )
                }

            } catch (e: Exception) {
                 android.util.Log.e("MessageRepository", "Failed to update conv or notify: ${e.message}")
                 updateFailed = true
            }
            
            if (updateFailed) {
                Result.failure(Exception("Message saved but conversation update failed"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🎤 SUBIR AUDIO
    suspend fun uploadAudio(conversationId: String, senderId: String, audioBytes: ByteArray): Result<String> {
        return try {
            val ext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) "ogg" else "m4a"
            val audioPath = "conversations/$conversationId/audio/${java.util.UUID.randomUUID()}.$ext"
            
            storageDataSource.uploadFile(audioPath, audioBytes)
        } catch (e: Exception) {
            android.util.Log.e("MessageRepository", "Error uploading audio", e)
            Result.failure(e)
        }
    }


    // ⌨️ TYPING STATUS (Round 67)
    suspend fun setTypingStatus(conversationId: String, userId: String, isTyping: Boolean): Result<Unit> {
        return try {
            val docRef = conversationsCollection.document(conversationId)
            if (isTyping) {
                docRef.update("typingUsers", com.google.firebase.firestore.FieldValue.arrayUnion(userId)).await()
            } else {
                docRef.update("typingUsers", com.google.firebase.firestore.FieldValue.arrayRemove(userId)).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            // Fail silently is mostly fine for typing status to avoid blocking UI
            Result.failure(e)
        }
    }

    // 👁️ MARK AS READ v3 (Round 67 - Phase 7)
    // Marks ALL unread messages in conversation as read by current user using pagination
    suspend fun markConversationAsRead(conversationId: String, userId: String): Result<Unit> {
        return try {
            // 1. Reset unread count (immediate visual feedback)
            conversationsCollection.document(conversationId).update(
                "unreadCount.$userId", 0
            ).await()

            // 2. Paginate through ALL messages to mark as read
            // 🚀 PERFORMANCE: Use pagination to handle conversations with many messages
            val pageSize = 100 // Firestore batch limit is 500, but we use 100 for safety
            var lastDocument: com.google.firebase.firestore.DocumentSnapshot? = null
            var totalUpdated = 0
            
            do {
                // Query messages in ascending order (oldest first) for pagination
                var query = conversationsCollection.document(conversationId)
                    .collection("messages")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .limit(pageSize.toLong())
                
                // Apply pagination cursor
                if (lastDocument != null) {
                    query = query.startAfter(lastDocument!!)
                }
                
                val messagesSnapshot = query.get().await()
                
                if (messagesSnapshot.isEmpty) {
                    break // No more messages
                }
                
                val batch = firestore.batch()
                var batchCount = 0
                
                for (doc in messagesSnapshot.documents) {
                    val readers = doc.get("readers") as? List<*> ?: emptyList<Any>()
                    
                    // Only update if user is not already in readers
                    if (!readers.contains(userId)) {
                        batch.update(doc.reference, "readers", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                        batchCount++
                        totalUpdated++
                    }
                }
                
                // Commit batch if there are updates
                if (batchCount > 0) {
                    batch.commit().await()
                }
                
                // Update pagination cursor
                lastDocument = messagesSnapshot.documents.lastOrNull()
                
            } while (lastDocument != null && messagesSnapshot.documents.size == pageSize)
            
            android.util.Log.d("MessageRepository", "Marked $totalUpdated messages as read for user $userId in conversation $conversationId")
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MessageRepository", "Error marking conversation as read: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // 👁️ MARK AS READ (Optimized version - only updates last N messages for performance)
    // Use this for quick visual feedback when user opens a chat
    suspend fun markConversationAsReadQuick(conversationId: String, userId: String, limit: Int = 50): Result<Unit> {
        return try {
            // 1. Reset unread count (immediate visual feedback)
            conversationsCollection.document(conversationId).update(
                "unreadCount.$userId", 0
            ).await()

            // 2. Update only the most recent messages (performance optimization)
            val messagesSnapshot = conversationsCollection.document(conversationId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val batch = firestore.batch()
            var updatesCount = 0

            for (doc in messagesSnapshot.documents) {
                val readers = doc.get("readers") as? List<*> ?: emptyList<Any>()
                if (!readers.contains(userId)) {
                    batch.update(doc.reference, "readers", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                    updatesCount++
                }
            }
            
            if (updatesCount > 0) {
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MessageRepository", "Error in quick mark as read: ${e.message}", e)
            Result.failure(e)
        }
    }

    // 🎭 TOGGLE REACTION (Round v1.1.4)
    suspend fun toggleReaction(
        conversationId: String,
        messageId: String,
        userId: String,
        emoji: String
    ): Result<Unit> {
        return try {
            val messageRef = conversationsCollection.document(conversationId)
                .collection("messages")
                .document(messageId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(messageRef)
                @Suppress("UNCHECKED_CAST")
                val currentReactions = snapshot.get("reactions") as? Map<String, List<String>> ?: emptyMap()
                val updatedReactions = currentReactions.toMutableMap()
                
                val userList = (updatedReactions[emoji] as? List<String>)?.toMutableList() ?: mutableListOf()
                
                if (userList.contains(userId)) {
                    // Remove reaction
                    userList.remove(userId)
                    if (userList.isEmpty()) {
                        updatedReactions.remove(emoji)
                    } else {
                        updatedReactions[emoji] = userList
                    }
                } else {
                    // Add reaction
                    userList.add(userId)
                    updatedReactions[emoji] = userList
                }
                
                transaction.update(messageRef, "reactions", updatedReactions)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MessageRepository", "Error toggling reaction: ${e.message}")
            Result.failure(e)
        }
    }

    // 🗑️ DELETE MESSAGE (Soft Delete)
    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> {
        return try {
            val messageDoc = conversationsCollection.document(conversationId)
                .collection("messages")
                .document(messageId)
            
            // 1. Mark as deleted
            messageDoc.update("isDeleted", true).await()

            // 2. Check if it was the last message to update the conversation preview
            try {
                val convDoc = conversationsCollection.document(conversationId)
                
                // If there's no clear way to know if it's "the" last without its timestamp, 
                // we can just check if the lastMessage field matches roughly or just update it 
                // if the deleted message is very recent.
                // A better way: Query the most recent non-deleted message to restore it as 'lastMessage'.
                
                val latestMessages = convDoc.collection("messages")
                    .whereEqualTo("isDeleted", false)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                if (latestMessages.isEmpty) {
                    // No more messages in conversation
                    convDoc.update(mapOf(
                        "lastMessage" to "Conversación vacía",
                        "lastMessageSenderId" to "",
                        "lastMessageTimestamp" to System.currentTimeMillis()
                    )).await()
                } else {
                    val last = latestMessages.documents.first().toObject(Message::class.java)
                    if (last != null) {
                        val displayContent = if (last.audioUrl != null) "🎤 Mensaje de audio" else last.content
                        convDoc.update(mapOf(
                            "lastMessage" to displayContent,
                            "lastMessageSenderId" to last.senderId,
                            "lastMessageTimestamp" to last.timestamp
                        )).await()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MessageRepository", "Failed to sync lastMessage after delete: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✏️ EDIT MESSAGE
    suspend fun editMessage(conversationId: String, messageId: String, oldContent: String, newContent: String): Result<Unit> {
        return try {
            conversationsCollection.document(conversationId)
                .collection("messages")
                .document(messageId)
                .update(
                    mapOf(
                        "content" to newContent,
                        "isEdited" to true,
                        "editHistory" to com.google.firebase.firestore.FieldValue.arrayUnion(oldContent)
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🗑️ DELETE CONVERSATION (Hard Delete)
    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        return try {
            // 1. Eliminar todos los mensajes de la subcolección
            val messagesSnapshot = conversationsCollection.document(conversationId)
                .collection("messages")
                .get()
                .await()

            val batch = firestore.batch()
            var count = 0
            for (doc in messagesSnapshot.documents) {
                batch.delete(doc.reference)
                count++
                // Firestore permite máximo 500 operaciones por batch
                if (count >= 450) {
                    batch.commit().await()
                    count = 0
                }
            }
            if (count > 0) {
                batch.commit().await()
            }

            // 2. Eliminar el documento de la conversación
            conversationsCollection.document(conversationId).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Observar mensajes de una conversación
    // Observar mensajes de una conversación con paginación
    fun observeMessages(conversationId: String, limit: Int = 30): Flow<List<Message>> = callbackFlow {
        // Query last 'limit' messages ordered by timestamp DESC
        val listener = conversationsCollection.document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MessageRepository", "Error observing messages: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                // Get messages, filter out deleted ones, deduplicate by ID (guards against
                // Firestore emitting the same message twice: once as a pending write and once
                // after server confirmation), then reverse to show oldest → newest.
                val messages = snapshot?.toObjects(Message::class.java)
                    ?.filter { !it.isDeleted }
                    ?.distinctBy { it.id }
                    ?.reversed() ?: emptyList()
                trySend(messages)
            }
        
        awaitClose { listener.remove() }
    }
    // 🎨 CHAT THEMES (Round 48)
    suspend fun updateConversationTheme(conversationId: String, themeColor: String): Result<Unit> {
        return try {
            conversationsCollection.document(conversationId).update("themeColor", themeColor).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Observar detalles de una conversación (Theme, Nicknames, etc.)
    fun observeConversation(conversationId: String): Flow<Conversation?> = callbackFlow {
        val listener = conversationsCollection.document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MessageRepository", "Error observing conversation: ${error.message}", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                val conversation = snapshot?.toObject(Conversation::class.java)
                trySend(conversation)
            }
        awaitClose { listener.remove() }
    }

}
