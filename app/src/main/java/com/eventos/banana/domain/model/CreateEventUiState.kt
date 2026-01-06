package com.eventos.banana.domain.model

data class CreateEventUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false
)
