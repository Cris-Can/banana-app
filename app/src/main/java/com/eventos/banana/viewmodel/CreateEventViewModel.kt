package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.domain.model.CreateEventUiState
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.JoinQuestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CreateEventViewModel(
    private val repository: EventRepository = EventRepository()
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
        val questions: List<JoinQuestion> = emptyList()
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

    private val subscriptionRepository = com.eventos.banana.data.repository.SubscriptionRepository()

    fun createEvent(event: Event, imageBytes: ByteArray? = null) {
        if (event.endAt <= event.startAt) {
            _uiState.value = _uiState.value.copy(errorMessage = "La fecha de término debe ser posterior al inicio")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Check Limits
            val canCreate = subscriptionRepository.canCreateEvent(event.creatorId)
            if (canCreate.isFailure || !canCreate.getOrDefault(false)) {
                _uiState.value = CreateEventUiState(
                    errorMessage = "Has alcanzado tu límite mensual de eventos gratuitos. ¡Mejórate a Premium!",
                    isLoading = false
                )
                return@launch
            }

            val result = repository.createEvent(event, imageBytes)

            _uiState.value = if (result.isSuccess) {
                // Increment Usage
                subscriptionRepository.incrementCreateCount(event.creatorId)
                CreateEventUiState(success = true)
            } else {
                CreateEventUiState(
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    private val _debugStatus = MutableStateFlow("Cargando limites...")
    val debugStatus: StateFlow<String> = _debugStatus

    fun loadDebugInfo(userId: String) {
        viewModelScope.launch {
            _debugStatus.value = "Cargando..."
            val stats = subscriptionRepository.getDebugStats(userId)
            _debugStatus.value = stats
        }
    }
    
    fun setDebugSubscription(userId: String, isPremium: Boolean) {
        viewModelScope.launch {
            val type = if (isPremium) com.eventos.banana.domain.model.SubscriptionType.PREMIUM else com.eventos.banana.domain.model.SubscriptionType.FREE
            subscriptionRepository.updateSubscriptionType(userId, type)
            // Refresh info
            loadDebugInfo(userId)
        }
    }
    fun resetState() {
        _uiState.value = CreateEventUiState()
    }
}
