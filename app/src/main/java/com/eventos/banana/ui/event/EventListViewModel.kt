package com.eventos.banana.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.EventListUiState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventType

import com.eventos.banana.data.repository.MainFeedRepository

@HiltViewModel
class EventListViewModel @Inject constructor(
    private val repository: EventRepository,
    private val mainFeedRepository: MainFeedRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20L
    }

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
    
    // Radius State
    private val _searchRadiusKm = MutableStateFlow(20) // Default 20km
    val searchRadiusKm: StateFlow<Int> = _searchRadiusKm.asStateFlow()
    
    fun updateRadius(radius: Int) {
        if (_searchRadiusKm.value != radius) {
            _searchRadiusKm.value = radius
            // Re-calculate geohash with new precision if location is known
            val loc = (_uiState.value as? EventListUiState.Success)?.currentUserLocation
            if (loc != null) {
                updateLocation(loc.latitude, loc.longitude) 
            }
        }
    }

    // Geolocation State
    private val _currentGeohash = MutableStateFlow<String?>(null)
    private val _currentCommune = MutableStateFlow<String?>(null)
    private val _currentRegion = MutableStateFlow<String?>(null)
    
    // 📍 Persistent Location (Independent of UI State)
    private var lastKnownLocation: com.eventos.banana.domain.model.ExactLocation? = null

    // 📄 Pagination
    private var currentLimit = PAGE_SIZE
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null
    
    // 📄 Paginación State
    private var lastVisibleDocument: com.google.firebase.firestore.DocumentSnapshot? = null
    private var isLastPage = false
    private val _currentEvents = MutableStateFlow<List<Event>>(emptyList())

    init {
        // Start observing with default (Global or cached)
        observeEvents()
        markFinishedEvents()
    }

    private fun markFinishedEvents() {
        viewModelScope.launch {
            try {
                repository.markFinishedEventsAsRatable()
            } catch (e: Exception) {
                android.util.Log.e("EventListViewModel", "Failed to mark events", e)
            }
        }
    }

    private var isGlobalSearch = false

    fun updateLocation(lat: Double, lng: Double) {
        // 💾 Store persistent location
        val newLoc = com.eventos.banana.domain.model.ExactLocation(lat, lng, "")
        lastKnownLocation = newLoc
        
        // Update State for UI immediately if possible
        val callbackState = _uiState.value
        if (callbackState is EventListUiState.Success) {
            _uiState.value = callbackState.copy(currentUserLocation = newLoc)
        }
        
        val radius = _searchRadiusKm.value
        
        val newHash = run {
            val precision = com.eventos.banana.util.GeohashUtils.getPrecisionForRadius(radius)
            com.eventos.banana.util.GeohashUtils.encode(lat, lng, precision)
        }
        
        // Always update geohash state
        _currentGeohash.value = newHash
        
        // Reset pagination on location change
        currentLimit = PAGE_SIZE
        
        // Refresh events
        if (_currentCommune.value.isNullOrBlank() && _currentRegion.value.isNullOrBlank() && !isGlobalSearch) {
             observeEvents(newHash, null, null)
        }
    }
    
    fun updateCommune(commune: String?) {
        if (_currentCommune.value != commune) {
            _currentCommune.value = commune
            currentLimit = PAGE_SIZE // Reset pagination
            isGlobalSearch = false
            observeEvents(_currentGeohash.value, commune, _currentRegion.value)
        }
    }

    fun updateRegion(region: String?) {
        if (_currentRegion.value != region) {
            _currentRegion.value = region
            currentLimit = PAGE_SIZE // Reset pagination
            isGlobalSearch = false
            observeEvents(_currentGeohash.value, _currentCommune.value, region)
        }
    }

    fun searchAllRegions() {
        _currentRegion.value = null
        _currentCommune.value = null
        isGlobalSearch = true
        currentLimit = PAGE_SIZE
        observeEvents(null, null, null)
    }

    /** Loads more events by requesting the next batch */
    fun loadMore() {
        if (_isLoadingMore.value || isLastPage) return
        _isLoadingMore.value = true
        val geo = if (isGlobalSearch) null else _currentGeohash.value
        observeEvents(geo, _currentCommune.value, _currentRegion.value, isLoadMore = true)
    }

    /** Refreshes the events list from the beginning */
    fun refresh() {
        val geo = if (isGlobalSearch) null else _currentGeohash.value
        observeEvents(geo, _currentCommune.value, _currentRegion.value, isLoadMore = false)
    }


    private fun observeEvents(geohash: String? = null, commune: String? = null, region: String? = null, isLoadMore: Boolean = false) {
        if (!isLoadMore) {
            lastVisibleDocument = null
            isLastPage = false
            _currentEvents.value = emptyList()
            currentLimit = PAGE_SIZE
        }

        if (isLastPage) {
            _isLoadingMore.value = false
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!isLoadMore) {
                val currentState = _uiState.value
                if (currentState is EventListUiState.Success) {
                    _uiState.value = currentState.copy(isRefreshing = true)
                } else {
                    _uiState.value = EventListUiState.Loading
                }
            }

            val result = mainFeedRepository.fetchEventsBatch(
                geohashPrefix = geohash,
                commune = commune,
                region = region,
                limit = currentLimit,
                lastSnapshot = lastVisibleDocument
            )

            result.onSuccess { (newEvents, newLastDoc) ->
                if (newEvents.isEmpty() && !isLoadMore) {
                    _uiState.value = EventListUiState.Success(emptyList(), emptyMap(), lastKnownLocation, false, isRefreshing = false)
                    isLastPage = true
                    _isLoadingMore.value = false
                    return@launch
                }

                if (newEvents.isEmpty() && isLoadMore) {
                    isLastPage = true
                    _isLoadingMore.value = false
                    val currentState = _uiState.value as? EventListUiState.Success
                    if (currentState != null) {
                        _uiState.value = currentState.copy(canLoadMore = false)
                    }
                    return@launch
                }
                
                lastVisibleDocument = newLastDoc

                val combinedEvents = if (isLoadMore) {
                    (_currentEvents.value + newEvents).distinctBy { it.id }
                } else {
                    newEvents
                }
                
                _currentEvents.value = combinedEvents

                var filteredEvents = combinedEvents

                // 📏 DISTANCE FILTERING (Client Side)
                if (commune.isNullOrBlank() && region.isNullOrBlank() && !isGlobalSearch) {
                     val userLoc = lastKnownLocation
                     
                     if (userLoc != null && geohash != null) {
                         val maxRadiusMeters = _searchRadiusKm.value * 1000f
                         val results = FloatArray(1)
                         
                         filteredEvents = combinedEvents.filter { event ->
                             val eLat = event.latitude
                             val eLng = event.longitude
                             
                             if (eLat != null && eLng != null) {
                                 try {
                                     android.location.Location.distanceBetween(
                                         userLoc.latitude, userLoc.longitude,
                                         eLat, eLng,
                                         results
                                     )
                                     results[0] <= maxRadiusMeters
                                 } catch (e: Exception) {
                                     false
                                 }
                             } else {
                                 false 
                             }
                         }
                     }
                }

                // Fetch creator profiles in parallel
                val creatorIds = filteredEvents.map { it.creatorId }.distinct()
                
                val profiles = creatorIds.map { uid ->
                    async {
                        try {
                            val profile = userRepository.getUserProfile(uid)
                            if (profile != null) uid to profile else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull().toMap()

                val canLoadMore = newEvents.size.toLong() >= currentLimit
                if (!canLoadMore) isLastPage = true

                _uiState.value = EventListUiState.Success(
                    events = filteredEvents, 
                    creatorProfiles = profiles, 
                    currentUserLocation = lastKnownLocation, 
                    canLoadMore = canLoadMore,
                    isRefreshing = false
                )
                _isLoadingMore.value = false
                
            }.onFailure { e ->
                android.util.Log.e("EventListViewModel", "Error in observeEvents: ${e.message}")
                if (!isLoadMore) {
                    _uiState.value = EventListUiState.Error("Error al cargar eventos: ${e.message}")
                }
                _isLoadingMore.value = false
            }
        }
    }
}

