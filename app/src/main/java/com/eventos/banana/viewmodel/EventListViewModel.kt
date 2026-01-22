package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.domain.model.EventListUiState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch


class EventListViewModel(
    private val repository: EventRepository = EventRepository()
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<EventListUiState>(EventListUiState.Loading)

    val uiState: StateFlow<EventListUiState> = _uiState

    init {
        observeEvents()
    }

    private fun observeEvents() {
        viewModelScope.launch {
            repository.observeEvents()
                .catch { e ->
                    _uiState.value = EventListUiState.Error("Error al cargar eventos: ${e.message}")
                }
                .collect { events ->
                    // Fetch creator profiles in parallel
                    val creatorIds = events.map { it.creatorId }.distinct()
                    val userRepository = com.eventos.banana.data.repository.UserRepository()
                    
                    val profiles = creatorIds.map { uid ->
                        async {
                            try {
                                val profile = userRepository.getUserProfile(uid)
                                if (profile != null) {
                                    uid to profile
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull().toMap()

                    _uiState.value = EventListUiState.Success(events, profiles)
                }
        }
    }
}

