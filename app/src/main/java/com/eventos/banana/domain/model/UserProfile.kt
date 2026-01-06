package com.eventos.banana.domain.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val nickname: String = "",
    val score: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
