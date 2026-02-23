package com.eventos.banana.domain.model

sealed interface EventListUiState {

    object Loading : EventListUiState

    data class Success(
        val events: List<Event>,
        val creatorProfiles: Map<String, UserProfile> = emptyMap(),
        val currentUserLocation: com.eventos.banana.domain.model.ExactLocation? = null,
        val canLoadMore: Boolean = false
    ) : EventListUiState

    data class Error(
        val message: String
    ) : EventListUiState
}
