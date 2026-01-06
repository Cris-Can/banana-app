package com.eventos.banana.domain.model

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
