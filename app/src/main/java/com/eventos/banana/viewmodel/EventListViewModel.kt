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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


import com.eventos.banana.domain.model.EventType

class EventListViewModel(
    private val repository: EventRepository = EventRepository()
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<EventListUiState>(EventListUiState.Loading)

    val uiState: StateFlow<EventListUiState> = _uiState
    
    // Filtering State
    private val _selectedCategory = MutableStateFlow<EventType?>(null)
    val selectedCategory: StateFlow<EventType?> = _selectedCategory

    fun selectCategory(type: EventType?) {
        _selectedCategory.value = if (_selectedCategory.value == type) null else type
    }

    private val _selectedDateFilter = MutableStateFlow<com.eventos.banana.domain.model.DateFilter>(com.eventos.banana.domain.model.DateFilter.ALL)
    val selectedDateFilter: StateFlow<com.eventos.banana.domain.model.DateFilter> = _selectedDateFilter.asStateFlow()

    fun selectDateFilter(filter: com.eventos.banana.domain.model.DateFilter) {
        _selectedDateFilter.value = filter
    }

    // Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

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

