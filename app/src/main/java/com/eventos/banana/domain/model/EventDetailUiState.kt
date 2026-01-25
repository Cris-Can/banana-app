package com.eventos.banana.domain.model

import com.eventos.banana.domain.model.UserProfile

sealed interface EventDetailUiState {

    object Loading : EventDetailUiState


    data class Success(
        val event: Event,
        val isJoining: Boolean = false,
        val userProfiles: Map<String, UserProfile> = emptyMap() // userId -> profile
    ) : EventDetailUiState
    
    data class Error(
        val message: String
    ) : EventDetailUiState
}
