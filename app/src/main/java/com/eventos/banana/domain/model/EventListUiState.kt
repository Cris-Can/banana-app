package com.eventos.banana.domain.model

sealed interface EventListUiState {

    object Loading : EventListUiState

    data class Success(
        val events: List<Event>,
        val creatorNicknames: Map<String, String> = emptyMap()
    ) : EventListUiState

    data class Error(
        val message: String
    ) : EventListUiState
}
