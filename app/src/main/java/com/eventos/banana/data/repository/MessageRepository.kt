package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.Conversation
import com.eventos.banana.domain.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MessageRepository {
    private val firestore = FirebaseFirestore.getInstance()
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
        content: String
    ): Result<Unit> {
        return try {
            val message = Message(
                conversationId = conversationId,
                senderId = senderId,
                content = content
            )
            
            val docRef = conversationsCollection.document(conversationId)
                .collection("messages")
                .document()
            
            docRef.set(message.copy(id = docRef.id)).await()
            
            // 📩 Update last message and increment unread count
            try {
                // Fetch to identify recipient for both unread count and notification
                val conversationSnapshot = conversationsCollection.document(conversationId).get().await()
                val participants = conversationSnapshot.get("participants") as? List<String>
                val recipientId = participants?.firstOrNull { it != senderId }
                
                val updates = mutableMapOf<String, Any>(
                    "lastMessage" to content,
                    "lastMessageSenderId" to senderId,
                    "lastMessageTimestamp" to System.currentTimeMillis()
                )
                
                if (recipientId != null) {
                    updates["unreadCount.$recipientId"] = com.google.firebase.firestore.FieldValue.increment(1)
                }

                conversationsCollection.document(conversationId).update(updates).await()

                // 🔔 Send Notification to Recipient
                if (recipientId != null) {
                    val notifRepo = NotificationRepository()
                    val senderNickname = (conversationSnapshot.get("participantNicknames") as? Map<String, String>)?.get(senderId) ?: "Alguien"
                    
                    notifRepo.sendNotification(
                        com.eventos.banana.domain.model.AppNotification(
                            userId = recipientId,
                            title = "Nuevo mensaje de $senderNickname",
                            message = content,
                            eventId = conversationId,
                            type = com.eventos.banana.domain.model.NotificationType.NEW_MESSAGE
                        )
                    )
                }

            } catch (e: Exception) {
                 android.util.Log.e("MessageRepository", "Failed to update conv or notify: ${e.message}")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
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

    // 👁️ MARK AS READ v2 (Round 67)
    // Marks all unread messages in conversation as read by current user
    suspend fun markConversationAsRead(conversationId: String, userId: String): Result<Unit> {
        return try {
            // 1. Reset unread count
            conversationsCollection.document(conversationId).update(
                "unreadCount.$userId", 0
            ).await()

            // 2. Add user to 'readers' of recent unread messages (Cost optimization: limit to last 20?)
            // We only update messages that don't have us in 'readers' yet.
            // Doing this for *every* read might be write-heavy.
            // Strategy: We will just update the "lastMessage" read status or do a batch update on unread ones.
            // For simplicity and cost balance: we won't batch update ALL past messages 'readers' field 
            // because that could be hundreds of writes.
            // We'll rely on 'unreadCount' for the "grey/blue" check on the CONVERSATION list.
            // But for individual messages (Double Check), we need to update the message docs.
            
            // Let's only update the last 20 messages to save writes, assuming older ones are scrolled past or read.
            val unreadMessagesQuery = conversationsCollection.document(conversationId)
                .collection("messages")
                .whereArrayContains("participants", userId) // Invalid query for subcollection usually
                // Better: just query messages where 'readers' does NOT contain userId? Firestore doesn't support "not-array-contains".
                // So we query last 20 orders by timestamp desc.
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()

            val batch = firestore.batch()
            var updatesCount = 0

            for (doc in unreadMessagesQuery.documents) {
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
            Result.success(Unit)
        }
    }

    // 🗑️ DELETE MESSAGE (Soft Delete)
    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> {
        return try {
            conversationsCollection.document(conversationId)
                .collection("messages")
                .document(messageId)
                .update("isDeleted", true)
                .await()
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
                
                // Get messages and reverse them to show oldest -> newest
                val messages = snapshot?.toObjects(Message::class.java)?.reversed() ?: emptyList()
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
