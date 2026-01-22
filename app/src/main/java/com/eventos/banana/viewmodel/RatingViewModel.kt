package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.RatingRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.EventType
import com.eventos.banana.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RatingUiState(
    val isLoading: Boolean = false,
    val usersToRate: List<UserProfile> = emptyList(),
    val alreadyRated: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class RatingViewModel(
    private val eventId: String,
    private val eventType: EventType,
    private val currentUserId: String,
    private val participantIds: List<String>, // Includes creator + approved participants
    private val ratingRepository: RatingRepository = RatingRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RatingUiState(isLoading = true))
    val uiState: StateFlow<RatingUiState> = _uiState

    init {
        loadUsersToRate()
    }

    private fun loadUsersToRate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // Get user profiles
                val profiles = participantIds
                    .filter { it != currentUserId } // Exclude self
                    .mapNotNull { userId ->
                        userRepository.getUserProfile(userId)
                    }
                
                // NUEVO (Round 12): Filtrar por encuentros confirmados
                val encounterRepo = com.eventos.banana.data.repository.EncounterRepository()
                val metUsers = encounterRepo.getEncountersForUser(eventId, currentUserId)
                    .getOrNull() ?: emptyList()
                
                // Solo filtrar si hay encuentros registrados para este evento
                val shouldFilter = encounterRepo.shouldEnforceEncounters(eventId)
                    .getOrDefault(false)
                
                val filteredProfiles = if (shouldFilter && metUsers.isNotEmpty()) {
                    profiles.filter { it.uid in metUsers }
                } else {
                    profiles // Fallback: mostrar todos si no hay encuentros
                }
                
                // Get already rated users
                val ratedResult = ratingRepository.getUsersToRate(
                    eventId = eventId,
                    currentUserId = currentUserId,
                    approvedParticipants = participantIds,
                    creatorId = participantIds.first() // Assume first is creator
                )
                
                val alreadyRatedIds = if (ratedResult.isSuccess) {
                    val pendingIds = ratedResult.getOrNull() ?: emptyList()
                    participantIds.filter { it !in pendingIds && it != currentUserId }.toSet()
                } else {
                    emptySet()
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    usersToRate = filteredProfiles,
                    alreadyRated = alreadyRatedIds
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al cargar participantes: ${e.message}"
                )
            }
        }
    }

    fun submitRating(
        toUserId: String,
        score: Int,
        comment: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            val result = ratingRepository.submitRating(
                eventId = eventId,
                eventType = eventType,
                fromUserId = currentUserId,
                toUserId = toUserId,
                score = score.toDouble(),
                comment = if (comment.isNullOrBlank()) null else comment
            )
            
            if (result.isSuccess) {
                // Add to already rated
                val newAlreadyRated = _uiState.value.alreadyRated + toUserId
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    alreadyRated = newAlreadyRated,
                    successMessage = "Puntuación enviada"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Error al enviar puntuación"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }
}
