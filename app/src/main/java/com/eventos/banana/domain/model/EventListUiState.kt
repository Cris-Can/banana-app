package com.eventos.banana.domain.model

sealed interface EventListUiState {

    object Loading : EventListUiState

    data class Success(
        val events: List<Event>
    ) : EventListUiState

    data class Error(
        val message: String
    ) : EventListUiState
}
