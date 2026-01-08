package com.eventos.banana.domain.model

sealed interface EventDetailUiState {

    object Loading : EventDetailUiState


    data class Success(
        val event: Event
    ) : EventDetailUiState

    data class Error(
        val message: String
    ) : EventDetailUiState
}
