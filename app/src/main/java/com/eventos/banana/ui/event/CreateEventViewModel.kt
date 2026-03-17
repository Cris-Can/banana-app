package com.eventos.banana.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.domain.model.CreateEventUiState
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.JoinQuestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val repository: EventRepository,
    private val createEventUseCase: com.eventos.banana.domain.usecase.event.CreateEventUseCase,
    private val subscriptionRepository: com.eventos.banana.data.repository.SubscriptionRepository
) : ViewModel() {

    // Helper class for form state
    data class EventFormState(
        val title: String = "",
        val description: String = "",
        val eventType: com.eventos.banana.domain.model.EventType = com.eventos.banana.domain.model.EventType.OTRO,
        val minimumScore: Double? = null, // null = sin restricción, o 3.0, 3.5, 4.0, 4.5
        val region: String = "",
        val commune: String = "",
        val address: String = "",
        val maxParticipants: String = "",
        val startAt: Long? = null,
        val endAt: Long? = null,
        val exactLocation: com.eventos.banana.domain.model.ExactLocation? = null,
        val selectedImageUri: android.net.Uri? = null,
        val currentLatitude: Double? = null,
        val currentLongitude: Double? = null,
        val questions: List<JoinQuestion> = emptyList(),
        val isPublic: Boolean = false, // 🌍 NEW
        val notificationRange: String = "COMMUNE" // 🔔 NEW: "COMMUNE", "REGION", "NATIONAL"
    )

    private val _formState = MutableStateFlow(EventFormState())
    val formState: StateFlow<EventFormState> = _formState

    fun updateTitle(value: String) { _formState.value = _formState.value.copy(title = value) }
    fun updateDescription(value: String) { _formState.value = _formState.value.copy(description = value) }
    fun updateEventType(value: com.eventos.banana.domain.model.EventType) { _formState.value = _formState.value.copy(eventType = value) }
    fun updateMinimumScore(value: Double?) { _formState.value = _formState.value.copy(minimumScore = value) }
    fun updateRegion(value: String) { _formState.value = _formState.value.copy(region = value) }
    fun updateCommune(value: String) { _formState.value = _formState.value.copy(commune = value) }
    fun updateAddress(value: String) { _formState.value = _formState.value.copy(address = value) }
    fun updateMaxParticipants(value: String) { _formState.value = _formState.value.copy(maxParticipants = value) }
    fun updateStartAt(value: Long?) { _formState.value = _formState.value.copy(startAt = value) }
    fun updateEndAt(value: Long?) { _formState.value = _formState.value.copy(endAt = value) }
    fun updateIsPublic(value: Boolean) { _formState.value = _formState.value.copy(isPublic = value) } // 🌍 NEW
    fun updateNotificationRange(value: String) { _formState.value = _formState.value.copy(notificationRange = value) } // 🔔 NEW

    fun updateExactLocation(value: com.eventos.banana.domain.model.ExactLocation?) { 
        _formState.value = _formState.value.copy(exactLocation = value) 
        // Auto-fill address if available
        if (value?.address?.isNotBlank() == true) {
            updateAddress(value.address)
        }
    }
    fun updateSelectedImageUri(value: android.net.Uri?) { _formState.value = _formState.value.copy(selectedImageUri = value) }

    fun updateLocationResult(region: String, commune: String, lat: Double?, lng: Double?) {
        _formState.value = _formState.value.copy(
            region = region, 
            commune = commune,
            currentLatitude = lat,
            currentLongitude = lng
        )
    }

    fun updateQuestions(questions: List<JoinQuestion>) {
        _formState.value = _formState.value.copy(questions = questions)
    }
    
    // Maintain current Lat/Long for map centered on user


    private val _uiState = MutableStateFlow(CreateEventUiState())
    val uiState: StateFlow<CreateEventUiState> = _uiState

    fun updateErrorMessage(message: String?) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    private val _limitDebugInfo = MutableStateFlow<String?>(null)
    val limitDebugInfo: StateFlow<String?> = _limitDebugInfo
    
    // NEW: Structured Data (Cleaner)
    private val _userLimitStats = MutableStateFlow<com.eventos.banana.data.repository.SubscriptionRepository.UserLimitStats?>(null)
    val userLimitStats: StateFlow<com.eventos.banana.data.repository.SubscriptionRepository.UserLimitStats?> = _userLimitStats

    fun createEvent(event: Event, imageBytes: ByteArray? = null) {
        if (event.endAt <= event.startAt) {
            _uiState.value = _uiState.value.copy(errorMessage = "La fecha de término debe ser posterior al inicio")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            _limitDebugInfo.value = null // Reset

            val result = createEventUseCase(event, imageBytes)

            if (result.isSuccess) {
                _uiState.value = CreateEventUiState(success = true)
            } else {
                val error = result.exceptionOrNull()
                if (error?.message == "LIMIT_REACHED") {
                    // Fetch Debug Info to help user understand WHY
                    val debug = subscriptionRepository.getDebugStats(event.creatorId)
                    _limitDebugInfo.value = debug
                    
                    // Fetch Structured Data
                    val stats = subscriptionRepository.getUserLimitStats(event.creatorId)
                    _userLimitStats.value = stats
                    
                    _uiState.value = CreateEventUiState(
                        errorMessage = "LIMIT_REACHED", // Special code for UI
                        isLoading = false
                    )
                } else {
                    _uiState.value = CreateEventUiState(
                        errorMessage = error?.message ?: "Error desconocido"
                    )
                }
            }
        }
    }

    private val _debugStatus = MutableStateFlow("Cargando limites...")
    val debugStatus: StateFlow<String> = _debugStatus

    fun loadUserStats(userId: String) {
        viewModelScope.launch {
            val stats = subscriptionRepository.getUserLimitStats(userId)
            _userLimitStats.value = stats
            
            // Also update legacy debug status if needed
            val debugText = subscriptionRepository.getDebugStats(userId)
            _debugStatus.value = debugText
        }
    }
    
    // 📺 ADS LOGIC
    private val _adUnlockState = MutableStateFlow<UnlockState>(UnlockState.Idle)
    val adUnlockState: StateFlow<UnlockState> = _adUnlockState
    
    sealed class UnlockState {
        object Idle : UnlockState()
        object LoadingAd : UnlockState()
        data class Progress(val watched: Int, val required: Int = 2) : UnlockState()
        object Unlocked : UnlockState()
        data class Error(val message: String) : UnlockState()
    }

    fun watchAd(activity: android.app.Activity, userId: String) {
        _adUnlockState.value = UnlockState.LoadingAd
        
        // Ensure Ad is loaded
        com.eventos.banana.util.AdMobHelper.loadRewardedAd(activity)
        
        // Show Ad
        com.eventos.banana.util.AdMobHelper.showRewardedAd(
            activity = activity,
            onUserEarnedReward = {
                // Update Repo
                viewModelScope.launch {
                    val result = subscriptionRepository.recordAdWatch(userId)
                    if (result.isSuccess) {
                         val (unlocked, progress) = result.getOrThrow()
                         if (progress == 0) { // Reset means we looped -> Unlocked!
                             _adUnlockState.value = UnlockState.Unlocked
                             // Re-evaluate create limit (will be done by UI calling createEvent again)
                         } else {
                             _adUnlockState.value = UnlockState.Progress(progress, 2)
                         }
                    } else {
                        _adUnlockState.value = UnlockState.Error("Error guardando progreso: ${result.exceptionOrNull()?.message}")
                    }
                }
            },
            onAdDismissed = {
                 if (_adUnlockState.value == UnlockState.LoadingAd) {
                     _adUnlockState.value = UnlockState.Idle // User closed without watching
                 }
            }
        )
    }

    fun setDebugSubscription(userId: String, isPremium: Boolean) {
        viewModelScope.launch {
            val type = if (isPremium) "GOLD" else "FREE"
            subscriptionRepository.updateSubscriptionType(userId, type)
            // Refresh info
            loadUserStats(userId)
        }
    }
    fun resetState() {
        _uiState.value = CreateEventUiState()
        _adUnlockState.value = UnlockState.Idle
    }
}
