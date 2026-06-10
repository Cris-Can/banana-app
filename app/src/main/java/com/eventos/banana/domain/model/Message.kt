package com.eventos.banana.domain.model

data class ConversationTheme(
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val backgroundColor: String? = null,
    val headerStyle: String = "default",
    val inputBarStyle: String = "default",
    val separatorStyle: String = "none",
    val bubbleAnimation: String = "slide",
    val bubbleShadow: String = "soft",
    val typingStyle: String = "dots"
)

data class Conversation(
    val id: String = "",
    val participants: List<String> = emptyList(),  // UIDs de los 2 usuarios
    val participantNicknames: Map<String, String> = emptyMap(),  // uid -> nickname
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Map<String, Int> = emptyMap(),  // uid -> cantidad no leídas
    val createdAt: Long = System.currentTimeMillis(),
    val themeColor: String? = null, // 🎨 Chat Theme (Hex or Key)
    val typingUsers: List<String> = emptyList(), // ⌨️ Users currently typing
    val theme: ConversationTheme? = null
)

fun Conversation.resolveTheme(): ConversationTheme {
    return theme ?: if (themeColor != null) {
        ConversationTheme(primaryColor = themeColor)
    } else {
        ConversationTheme()
    }
}

data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false, // Deprecated in favor of readers list, kept for backward compat?
    val readers: List<String> = emptyList(), // 👁️ UIDs who read this message
    @get:com.google.firebase.firestore.PropertyName("isDeleted")
    @set:com.google.firebase.firestore.PropertyName("isDeleted")
    var isDeleted: Boolean = false, // 🗑️ Soft delete
    val isEdited: Boolean = false, // ✏️ Has been edited
    val editHistory: List<String> = emptyList(), // 📜 Previous content versions
    val replyToId: String? = null, // ↩️ Reply to message ID
    val audioUrl: String? = null, // 🎤 Audio message URL
    val audioDurationMs: Int? = null, // 🕒 Audio duration in milliseconds
    val audioWaveform: List<Float>? = null, // 🌊 Waveform data
    val reactions: Map<String, List<String>> = emptyMap() // ❤️ emoji -> list of user UIDs
)
