package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.domain.model.EventDetailUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EventDetailViewModel(
    private val eventId: String,
    private val repository: EventRepository = EventRepository()
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)

    val uiState: StateFlow<EventDetailUiState> = _uiState

    init {
        loadEvent()
    }

    private fun loadEvent() {
        viewModelScope.launch {
            val result = repository.getEventById(eventId)

            _uiState.value = result.fold(
                onSuccess = { EventDetailUiState.Success(it) },
                onFailure = {
                    EventDetailUiState.Error(
                        it.message ?: "Error al cargar el evento"
                    )
                }
            )
        }
    }

    fun requestJoinEvent(userId: String) {
        viewModelScope.launch {
            repository.requestJoinEvent(eventId, userId)
            loadEvent()
        }
    }

    fun approveParticipant(userId: String) {
        viewModelScope.launch {
            repository.approveParticipant(eventId, userId)
            loadEvent()
        }
    }

    fun rejectParticipant(userId: String) {
        viewModelScope.launch {
            repository.rejectParticipant(eventId, userId)
            loadEvent()
        }
    }
}
