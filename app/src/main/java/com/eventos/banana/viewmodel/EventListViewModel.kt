package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.domain.model.EventListUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EventListViewModel(
    private val repository: EventRepository = EventRepository()
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<EventListUiState>(EventListUiState.Loading)

    val uiState: StateFlow<EventListUiState> = _uiState

    init {
        loadEvents()
    }

     fun loadEvents() {
        viewModelScope.launch {
            _uiState.value = EventListUiState.Loading

            val result = repository.getEvents()

            _uiState.value = result.fold(
                onSuccess = { events ->
                    EventListUiState.Success(events)
                },
                onFailure = { error ->
                    EventListUiState.Error(
                        error.message ?: "Error al cargar eventos"
                    )
                }
            )
        }
    }
}
