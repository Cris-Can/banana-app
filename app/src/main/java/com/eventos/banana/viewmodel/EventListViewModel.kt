package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.domain.model.EventListUiState
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
                    // Fetch creator nicknames
                    val creatorIds = events.map { it.creatorId }.distinct()
                    val creatorNicknames = mutableMapOf<String, String>()
                    
                    // Note: Ideally cache this or use a more efficient batch fetch if possible.
                    // For now, we fetch individually but could optimize.
                    // We need a UserRepository access here.
                    // Since I cannot easily inject UserRepository here without changing constructor signature and breaking calls,
                    // I will instantiate it here as a quick fix, assuming it has a default constructor (which it does).
                    
                    val userRepository = com.eventos.banana.data.repository.UserRepository()
                    
                    creatorIds.forEach { uid ->
                         try {
                             val profile = userRepository.getUserProfile(uid)
                             creatorNicknames[uid] = profile?.nickname ?: "Usuario"
                         } catch (e: Exception) {
                             creatorNicknames[uid] = "Usuario"
                         }
                    }

                    _uiState.value = EventListUiState.Success(events, creatorNicknames)
                }
        }
    }
}
