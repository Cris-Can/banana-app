package com.eventos.banana.domain.model

sealed interface EventDetailUiState {

    object Loading : EventDetailUiState


    data class Success(
        val event: Event,
        val isJoining: Boolean = false,
        val userNicknames: Map<String, String> = emptyMap() // userId -> nickname
    ) : EventDetailUiState

    data class Error(
        val message: String
    ) : EventDetailUiState
}
