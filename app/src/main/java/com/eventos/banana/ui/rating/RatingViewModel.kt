package com.eventos.banana.ui.rating

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import timber.log.Timber
import com.eventos.banana.data.repository.RatingRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.EventType
import com.eventos.banana.domain.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import com.eventos.banana.data.repository.EncounterRepository
import com.eventos.banana.domain.usecase.rating.GetPendingRatingsUseCase

data class RatingUiState(
    val isLoading: Boolean = false,
    val usersToRate: List<UserProfile> = emptyList(),
    val alreadyRated: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isSkipped: Boolean = false,
    val submittingUserId: String? = null
)



@HiltViewModel(assistedFactory = RatingViewModel.Factory::class)
class RatingViewModel @AssistedInject constructor(
    @Assisted("rating_eventId") private val eventId: String,
    @Assisted("rating_eventType") private val eventType: EventType,
    @Assisted("rating_currentUserId") private val currentUserId: String,
    @Assisted("rating_participantIds") private val participantIds: List<String>,
    private val userRepository: UserRepository,
    private val ratingRepository: RatingRepository,
    private val encounterRepo: EncounterRepository,
    private val getPendingRatingsUseCase: GetPendingRatingsUseCase
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("rating_eventId") eventId: String,
            @Assisted("rating_eventType") eventType: EventType,
            @Assisted("rating_currentUserId") currentUserId: String,
            @Assisted("rating_participantIds") participantIds: List<String>
        ): RatingViewModel
    }


    private val _uiState = MutableStateFlow(RatingUiState(isLoading = true))
    val uiState: StateFlow<RatingUiState> = _uiState

    // Active jobs — cancelled and restarted to prevent duplicate coroutines
    private var loadJob: Job? = null
    private var submitJob: Job? = null
    private var skipJob: Job? = null

    init {
        loadUsersToRate()
    }

    private fun loadUsersToRate() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val profiles = userRepository.getUsers(
                    participantIds.filter { it != currentUserId }
                ).distinctBy { it.uid }
                
                // Filtrar por encuentros confirmados
                val encountersResult = encounterRepo.getEncountersForUser(eventId, currentUserId)
                val metUsers = encountersResult.getOrNull().also {
                    if (it == null) {
                        Timber.w(encountersResult.exceptionOrNull(), "Failed to load encounters for user $currentUserId in event $eventId")
                    }
                } ?: emptyList()
                
                // Solo filtrar si hay encuentros registrados para este evento
                val shouldFilter = encounterRepo.shouldEnforceEncounters(eventId)
                    .getOrDefault(false)
                
                val filteredProfiles = if (shouldFilter && metUsers.isNotEmpty()) {
                    profiles.filter { it.uid in metUsers }
                } else {
                    profiles // Fallback: mostrar todos si no hay encuentros
                }

                // Verificar si ha saltado la calificación para este evento
                val hasSkipped = ratingRepository.hasSkippedRating(currentUserId, eventId).getOrDefault(false)
                if (hasSkipped) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        usersToRate = emptyList(),
                        alreadyRated = emptySet(),
                        isSkipped = true
                    )
                    return@launch
                }
                
                // Obtener EXPRESAMENTE los usuarios que ya calificamos en la DB
                val alreadyRatedResult = ratingRepository.getAlreadyRatedUsers(eventId, currentUserId)
                val alreadyRatedIds = alreadyRatedResult.getOrNull().also {
                    if (it == null) {
                        Timber.e(alreadyRatedResult.exceptionOrNull(), "Failed to load already rated users for user $currentUserId in event $eventId")
                    }
                } ?: emptySet()
                
                Log.d("RatingViewModel", "Already rated IDs for event $eventId: $alreadyRatedIds")
                
                // Get pending users (normalizando los UIDs por seguridad extra)
                val pendingUsers = filteredProfiles.filter { profile ->
                    profile.uid.trim() !in alreadyRatedIds
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    usersToRate = pendingUsers,
                    alreadyRated = alreadyRatedIds
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, submittingUserId = toUserId)
            
            val result = ratingRepository.submitRating(
                eventId = eventId,
                eventType = eventType,
                fromUserId = currentUserId,
                toUserId = toUserId,
                score = score.toDouble(),
                comment = if (comment.isNullOrBlank()) null else comment
            )
            
            if (result.isSuccess) {
                // Normalizamos el ID para evitar fallos por espacios
                val targetId = toUserId.trim()
                
                _uiState.update { currentState ->
                    val beforeCount = currentState.usersToRate.size
                    val newUsersToRate = currentState.usersToRate.filter { it.uid.trim() != targetId }
                    val afterCount = newUsersToRate.size
                    
                    Log.d("RatingViewModel", "Atomic update for $targetId. Size: $beforeCount -> $afterCount")
                    
                    currentState.copy(
                        isLoading = false,
                        usersToRate = newUsersToRate,
                        alreadyRated = currentState.alreadyRated + targetId,
                        successMessage = "Puntuación enviada",
                        submittingUserId = null
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Error al enviar puntuación",
                    submittingUserId = null
                )
            }
        }
    }

    fun skipRating() {
        skipJob?.cancel()
        skipJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = ratingRepository.skipRating(currentUserId, eventId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSkipped = true,
                    successMessage = "Recordatorios desactivados para este evento"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al omitir: ${result.exceptionOrNull()?.message}"
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

    override fun onCleared() {
        loadJob?.cancel()
        submitJob?.cancel()
        skipJob?.cancel()
        super.onCleared()
    }
}
