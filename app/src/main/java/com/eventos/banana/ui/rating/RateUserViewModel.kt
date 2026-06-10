package com.eventos.banana.ui.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.core.security.RateLimitManager
import com.eventos.banana.data.repository.RatingRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.EventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import com.eventos.banana.ui.util.ResultState

data class RateUserUiState(
    val submissionState: ResultState<Unit> = ResultState.Idle,
    val targetNickname: String = "",
    val alreadyRated: Boolean = false,
    val isLoadingData: Boolean = true,
    val loadError: String? = null
)

@HiltViewModel(assistedFactory = RateUserViewModel.Factory::class)
class RateUserViewModel @AssistedInject constructor(
    @Assisted("rateuser_targetUserId") private val targetUserId: String,
    @Assisted("rateuser_eventId") private val eventId: String,
    @Assisted("rateuser_currentUserId") private val currentUserId: String,
    private val ratingRepository: RatingRepository,
    private val userRepository: UserRepository,
    private val rateLimitManager: RateLimitManager
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("rateuser_targetUserId") targetUserId: String,
            @Assisted("rateuser_eventId") eventId: String,
            @Assisted("rateuser_currentUserId") currentUserId: String
        ): RateUserViewModel
    }


    private val _uiState = MutableStateFlow(RateUserUiState())
    val uiState: StateFlow<RateUserUiState> = _uiState

    // Active jobs — cancelled and restarted to prevent duplicate coroutines
    private var loadJob: Job? = null
    private var submitJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                // 1. Check if already rated
                val rated = ratingRepository.hasUserRated(eventId, currentUserId, targetUserId)
                
                // 2. Get target user profile
                val targetProfile = userRepository.getUserProfile(targetUserId)
                
                _uiState.value = _uiState.value.copy(
                    isLoadingData = false,
                    targetNickname = targetProfile?.nickname ?: "Usuario",
                    alreadyRated = rated
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoadingData = false,
                    loadError = "Error al cargar datos: ${e.message}"
                )
            }
        }
    }

    fun submitRating(score: Int, comment: String) {
        if (score < 1) return

        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(submissionState = ResultState.Loading)

            try {
                // Rate limiting check before submission
                val rateLimitResult = rateLimitManager.checkRateLimit(RateLimitManager.ACTION_RATING)
                if (!rateLimitResult.success) {
                    _uiState.value = _uiState.value.copy(
                        submissionState = ResultState.Error(
                            "Demasiados intentos. Espera ${rateLimitResult.timeUntilReset} e inténtalo de nuevo."
                        )
                    )
                    return@launch
                }

                val result = ratingRepository.submitRating(
                    eventId = eventId,
                    eventType = EventType.OTRO,
                    fromUserId = currentUserId,
                    toUserId = targetUserId,
                    score = score.toDouble(),
                    comment = if (comment.isBlank()) null else comment
                )

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(submissionState = ResultState.Success(Unit))
                } else {
                    _uiState.value = _uiState.value.copy(
                        submissionState = ResultState.Error(result.exceptionOrNull()?.message ?: "Error desconocido")
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    submissionState = ResultState.Error(e.message ?: "Error fatal")
                )
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        submitJob?.cancel()
        super.onCleared()
    }
}

