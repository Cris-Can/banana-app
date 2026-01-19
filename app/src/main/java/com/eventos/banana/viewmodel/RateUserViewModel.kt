package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.RatingRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.Rating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RateUserUiState(
    val isLoading: Boolean = false,
    val targetNickname: String = "",
    val success: Boolean = false,
    val alreadyRated: Boolean = false,
    val errorMessage: String? = null
)

class RateUserViewModel(
    private val targetUserId: String,
    private val eventId: String,
    private val currentUserId: String,
    private val ratingRepository: RatingRepository = RatingRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RateUserUiState(isLoading = true))
    val uiState: StateFlow<RateUserUiState> = _uiState

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                // 1. Check if already rated
                val rated = ratingRepository.hasUserRated(eventId, currentUserId, targetUserId)
                
                // 2. Get target user profile
                val targetProfile = userRepository.getUserProfile(targetUserId)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    targetNickname = targetProfile?.nickname ?: "Usuario",
                    alreadyRated = rated
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al cargar datos: ${e.message}"
                )
            }
        }
    }

    fun submitRating(score: Int, comment: String) {
        if (score < 1) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val rating = Rating(
                fromUserId = currentUserId,
                toUserId = targetUserId,
                eventId = eventId,
                score = score,
                comment = comment
            )

            val result = ratingRepository.submitRating(rating)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, success = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al enviar: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }
}
