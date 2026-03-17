package com.eventos.banana.domain.model

import java.util.Date
import com.google.firebase.firestore.PropertyName

data class FeedPost(
    val id: String = "",
    val eventId: String = "",
    val userId: String = "",
    val userNickname: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val timestamp: Any? = null,
    @PropertyName("isUserVerified") val isUserVerified: Boolean = false,
    val replyToId: String? = null,
    val replyToNickname: String? = null,
    val replyToContent: String? = null,
    val replyToUserId: String? = null
) {
    // Helper for UI - convert timestamp to Date safely
    val timestampAsDate: Date?
        get() = when (timestamp) {
            is Long -> Date(timestamp)
            is com.google.firebase.Timestamp -> Date(timestamp.seconds * 1000)
            is Date -> timestamp
            else -> null
        }
}
