package com.eventos.banana.domain.model

data class JoinRequest(
    val userId: String = "",
    val userNickname: String = "",
    val answers: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L
)
