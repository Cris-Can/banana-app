package com.eventos.banana.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest

data class RankingUiState(
    val isLoading: Boolean = true,
    val topUsersByScore: List<UserProfile> = emptyList(),
    val topUsersByRating: List<UserProfile> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class RankingViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    // Cache de bloqueados del usuario actual para evitar lecturas repetidas
    private var blockedUsersCache: Set<String> = emptySet()

    // Active listener jobs — cancelled and restarted each time loadRankings() is called
    private var scoreJob: Job? = null
    private var ratingJob: Job? = null

    fun loadRankings(currentUserId: String? = null) {
        scoreJob?.cancel()
        ratingJob?.cancel()

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            // 1. Cargar bloqueados PRIMERO antes de arrancar listeners
            if (currentUserId != null) {
                val profile = userRepository.getUserProfile(currentUserId)
                blockedUsersCache = profile?.blockedUsers?.toSet() ?: emptySet()
            }

            // 2. AHORA sí arrancar listeners — cache ya está disponible
            scoreJob = launch {
                try {
                    userRepository.observeTopUsers(limit = 20)
                        .collectLatest { rawList ->
                            val filtered = rawList.filter { !it.isBanned && !it.admin && it.uid !in blockedUsersCache }
                            _uiState.update { it.copy(isLoading = false, topUsersByScore = filtered) }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("RankingViewModel", "observeTopUsers error", e)
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "No se pudo cargar el ranking de puntos.")
                    }
                }
            }

            ratingJob = launch {
                try {
                    userRepository.observeTopUsersByRating(limit = 20)
                        .collectLatest { rawList ->
                            val filtered = rawList.filter { !it.isBanned && !it.admin && it.uid !in blockedUsersCache }
                            _uiState.update { it.copy(isLoading = false, topUsersByRating = filtered) }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("RankingViewModel", "observeTopUsersByRating error", e)
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = state.errorMessage ?: e.message ?: "No se pudo cargar el ranking de valoraciones."
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        scoreJob?.cancel()
        ratingJob?.cancel()
        super.onCleared()
    }
}
