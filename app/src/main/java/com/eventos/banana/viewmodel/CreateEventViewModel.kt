package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.domain.model.CreateEventUiState
import com.eventos.banana.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CreateEventViewModel(
    private val repository: EventRepository = EventRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateEventUiState())
    val uiState: StateFlow<CreateEventUiState> = _uiState

    fun createEvent(event: Event) {
        viewModelScope.launch {

            _uiState.value = CreateEventUiState(isLoading = true)

            val result = repository.createEvent(event)

            _uiState.value = if (result.isSuccess) {
                CreateEventUiState(success = true)
            } else {
                CreateEventUiState(
                    errorMessage = "No se pudo crear el evento"
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = CreateEventUiState()
    }
}
