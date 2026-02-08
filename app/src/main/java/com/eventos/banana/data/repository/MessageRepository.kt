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
                    close(error)
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
    // Marcar conversación como leída
    suspend fun markConversationAsRead(conversationId: String, userId: String): Result<Unit> {
        return try {
            conversationsCollection.document(conversationId).update(
                "unreadCount.$userId", 0
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            // Ignore error if field doesn't exist yet
            Result.success(Unit)
        }
    }

    // Observar mensajes de una conversación
    fun observeMessages(conversationId: String): Flow<List<Message>> = callbackFlow {
        val listener = conversationsCollection.document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
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
                    close(error)
                    return@addSnapshotListener
                }
                val conversation = snapshot?.toObject(Conversation::class.java)
                trySend(conversation)
            }
        awaitClose { listener.remove() }
    }
}
