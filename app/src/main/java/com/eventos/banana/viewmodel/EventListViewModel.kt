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

    init {
        // Start observing with default (Global or cached)
        observeEvents()
    }

    fun updateLocation(lat: Double, lng: Double) {
        // 💾 Store persistent location
        val newLoc = com.eventos.banana.domain.model.ExactLocation(lat, lng, "")
        lastKnownLocation = newLoc
        
        // Update State for UI immediately if possible
        val callbackState = _uiState.value
        if (callbackState is EventListUiState.Success) {
            _uiState.value = callbackState.copy(currentUserLocation = newLoc)
        }
        
        // Encode based on radius
        // ⚠️ CRITICAL OBS: Geohash query returns 0 results for existing data (likely missing 'geohash' field).
        // FIX: Force Global Search + Client Distance Filter for ALL radii temporarily.
        val radius = _searchRadiusKm.value
        val useGeohash = true 
        
        android.util.Log.d("EventListViewModel", "📍 updateLocation: $lat, $lng. Radius: $radius. UseGeohash: $useGeohash")
        
        val newHash = if (useGeohash) {
            val precision = com.eventos.banana.util.GeohashUtils.getPrecisionForRadius(radius)
            com.eventos.banana.util.GeohashUtils.encode(lat, lng, precision)
        } else {
            null // Force Global Query
        }
        
        // Always update geohash state
        _currentGeohash.value = newHash
        
        // Refresh events
        if (_currentCommune.value.isNullOrBlank() && _currentRegion.value.isNullOrBlank()) {
             observeEvents(newHash, null, null)
        }
    }
    
    fun updateCommune(commune: String?) {
        if (_currentCommune.value != commune) {
            _currentCommune.value = commune
            // Use blank-safe params
            observeEvents(_currentGeohash.value, commune, _currentRegion.value)
        }
    }

    fun updateRegion(region: String?) {
        if (_currentRegion.value != region) {
            _currentRegion.value = region
            observeEvents(_currentGeohash.value, _currentCommune.value, region)
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun observeEvents(geohash: String? = null, commune: String? = null, region: String? = null) {
        android.util.Log.d("EventListViewModel", "⚡ observeEvents Called. Geohash: $geohash, Commune: '$commune', Region: '$region'")
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            repository.observeNearbyEvents(geohash, commune, region)
                .catch { e ->
                    android.util.Log.e("EventListViewModel", "❌ Error in observeEvents: ${e.message}")
                    _uiState.value = EventListUiState.Error("Error al cargar eventos: ${e.message}")
                }
                .collect { events ->
                    android.util.Log.d("EventListViewModel", "📥 Events Received from Repo: ${events.size}")
                    var filteredEvents = events
                    
                    // 📏 DISTANCE FILTERING (Client Side)
                    // Apply if NOT filtering by Commune/Region AND we have a User Location
                    if (commune.isNullOrBlank() && region.isNullOrBlank()) {
                         // 📍 Use Persistent Location
                         val userLoc = lastKnownLocation
                         
                         if (userLoc != null) {
                             val maxRadiusMeters = _searchRadiusKm.value * 1000f
                             val results = FloatArray(1)
                             
                             android.util.Log.d("EventListViewModel", "🔍 Filtering by Distance. Radius: ${_searchRadiusKm.value}km, UserLoc: ${userLoc.latitude},${userLoc.longitude}")
                             
                             filteredEvents = events.filter { event ->
                                 val eLat = event.latitude
                                 val eLng = event.longitude
                                 
                                 android.util.Log.d("EventListViewModel", "🧐 Checking Event '${event.title}' (${event.id}). Lat: $eLat, Lng: $eLng")
                                 
                                 if (eLat != null && eLng != null) {
                                     android.location.Location.distanceBetween(
                                         userLoc.latitude, userLoc.longitude,
                                         eLat, eLng,
                                         results
                                     )
                                     val distance = results[0]
                                     val isInside = distance <= maxRadiusMeters
                                     
                                     if (isInside) {
                                         android.util.Log.d("EventListViewModel", "✅ Event '${event.title}' INCLUDED. Dist: ${distance}m <= ${maxRadiusMeters}m")
                                     } else {
                                         android.util.Log.d("EventListViewModel", "❌ Event '${event.title}' EXCLUDED. Dist: ${distance}m > ${maxRadiusMeters}m")
                                     }
                                     
                                     isInside
                                 } else {
                                     android.util.Log.d("EventListViewModel", "❌ Event '${event.title}' EXCLUDED (No Location)")
                                     false 
                                 }
                             }
                             android.util.Log.d("EventListViewModel", "📊 Filter Result: ${filteredEvents.size} events passed out of ${events.size}")
                         } else {
                             android.util.Log.w("EventListViewModel", "⚠️ User Location IS NULL during filter. Showing all events (Fail Open).")
                         }
                    }

                    // Fetch creator profiles in parallel
                    val creatorIds = filteredEvents.map { it.creatorId }.distinct()
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

                    _uiState.value = EventListUiState.Success(filteredEvents, profiles, lastKnownLocation)
                }
        }
    }
}

