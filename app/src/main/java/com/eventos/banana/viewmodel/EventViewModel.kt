package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.domain.model.CreateEventUiState
import com.eventos.banana.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EventViewModel(
    private val eventRepository: EventRepository = EventRepository()
) : ViewModel() {

    private val _createEventUiState =
        MutableStateFlow(CreateEventUiState())

    val createEventUiState: StateFlow<CreateEventUiState> =
        _createEventUiState

    fun createEvent(event: Event) {
        viewModelScope.launch {

            // ✅ VALIDACIONES BÁSICAS
            if (
                event.title.isBlank() ||
                event.description.isBlank() ||
                event.region.isBlank() ||
                event.commune.isBlank() ||
                event.maxParticipants <= 0
            ) {
                _createEventUiState.value = CreateEventUiState(
                    errorMessage = "Completa todos los campos correctamente"
                )
                return@launch
            }

            _createEventUiState.value = CreateEventUiState(isLoading = true)

            val result = eventRepository.createEvent(event)

            _createEventUiState.value =
                if (result.isSuccess) {
                    CreateEventUiState(success = true)
                } else {
                    CreateEventUiState(
                        errorMessage = "No se pudo crear el evento"
                    )
                }
        }
    }


    fun resetState() {
        _createEventUiState.value = CreateEventUiState()
    }
}
