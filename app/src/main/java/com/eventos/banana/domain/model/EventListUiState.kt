package com.eventos.banana.domain.model

sealed interface EventListUiState {

    val selectedCategory: EventType?
    val selectedDateFilter: DateFilter

    data class Loading(
        override val selectedCategory: EventType? = null,
        override val selectedDateFilter: DateFilter = DateFilter.ALL
    ) : EventListUiState

    data class Success(
        val events: List<Event>,
        val creatorProfiles: Map<String, UserProfile> = emptyMap(),
        val currentUserLocation: ExactLocation? = null,
        val canLoadMore: Boolean = false,
        val isRefreshing: Boolean = false,
        override val selectedCategory: EventType? = null,
        override val selectedDateFilter: DateFilter = DateFilter.ALL
    ) : EventListUiState

    data class Error(
        val message: String,
        override val selectedCategory: EventType? = null,
        override val selectedDateFilter: DateFilter = DateFilter.ALL
    ) : EventListUiState
}
