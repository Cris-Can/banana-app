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

import com.google.firebase.firestore.DocumentSnapshot

data class RankingUiState(
    val isLoading: Boolean = true,
    val isLoadingMoreScore: Boolean = false,
    val isLoadingMoreRating: Boolean = false,
    val topUsersByScore: List<UserProfile> = emptyList(),
    val topUsersByRating: List<UserProfile> = emptyList(),
    val scoreLastVisible: DocumentSnapshot? = null,
    val ratingLastVisible: DocumentSnapshot? = null,
    val hasMoreScore: Boolean = true,
    val hasMoreRating: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class RankingViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    init {
        loadRankings()
    }

    fun loadRankings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            val limit = 20
            val scoreResult = userRepository.getTopUsers(limit = limit)
            val ratingResult = userRepository.getTopUsersByRating(limit = limit)
            
            if (scoreResult.isSuccess && ratingResult.isSuccess) {
                val scorePair = scoreResult.getOrNull()
                val ratingPair = ratingResult.getOrNull()
                
                // Filtramos a los baneados y administradores por si acaso (Trust & Safety)
                val topScore = scorePair?.first?.filter { !it.isBanned && !it.isAdmin } ?: emptyList()
                val topRating = ratingPair?.first?.filter { !it.isBanned && !it.isAdmin } ?: emptyList()
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        topUsersByScore = topScore,
                        topUsersByRating = topRating,
                        scoreLastVisible = scorePair?.second,
                        ratingLastVisible = ratingPair?.second,
                        hasMoreScore = (scorePair?.first?.size ?: 0) >= limit,
                        hasMoreRating = (ratingPair?.first?.size ?: 0) >= limit
                    )
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "No se pudieron cargar los rankings. Revisa tu conexión."
                    )
                }
            }
        }
    }

    fun loadMore(isScoreMode: Boolean) {
        val currentState = _uiState.value
        val limit = 20

        if (isScoreMode) {
            if (currentState.isLoadingMoreScore || !currentState.hasMoreScore) return
            
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMoreScore = true) }
                val result = userRepository.getTopUsers(limit = limit, startAfter = currentState.scoreLastVisible)
                
                if (result.isSuccess) {
                    val pair = result.getOrNull()
                    val newUsers = pair?.first?.filter { !it.isBanned && !it.isAdmin } ?: emptyList()
                    _uiState.update { 
                        it.copy(
                            isLoadingMoreScore = false,
                            topUsersByScore = it.topUsersByScore + newUsers,
                            scoreLastVisible = pair?.second,
                            hasMoreScore = (pair?.first?.size ?: 0) >= limit
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingMoreScore = false) }
                }
            }
        } else {
            if (currentState.isLoadingMoreRating || !currentState.hasMoreRating) return
            
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMoreRating = true) }
                val result = userRepository.getTopUsersByRating(limit = limit, startAfter = currentState.ratingLastVisible)
                
                if (result.isSuccess) {
                    val pair = result.getOrNull()
                    val newUsers = pair?.first?.filter { !it.isBanned && !it.isAdmin } ?: emptyList()
                    _uiState.update { 
                        it.copy(
                            isLoadingMoreRating = false,
                            topUsersByRating = it.topUsersByRating + newUsers,
                            ratingLastVisible = pair?.second,
                            hasMoreRating = (pair?.first?.size ?: 0) >= limit
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingMoreRating = false) }
                }
            }
        }
    }
}
