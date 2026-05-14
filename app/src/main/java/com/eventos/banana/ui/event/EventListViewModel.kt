package com.eventos.banana.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.data.repository.MainFeedRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.DateFilter
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventListUiState
import com.eventos.banana.domain.model.EventStatus
import com.eventos.banana.domain.model.EventType
import com.eventos.banana.domain.model.ExactLocation
import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.util.AppConstants
import com.eventos.banana.util.GeohashUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EventQueryParams(
    val geohash: String?,
    val radiusKm: Int = AppConstants.DEFAULT_SEARCH_RADIUS_KM,
    val category: EventType? = null,
    val dateFilter: DateFilter = DateFilter.ALL
)

sealed class DataState<out T> {
    object Loading : DataState<Nothing>()
    data class Success<T>(val data: T) : DataState<T>()
    data class Error(val exception: Throwable) : DataState<Nothing>()
}

private data class PaginationState(
    val lastVisibleDoc: com.google.firebase.firestore.DocumentSnapshot? = null,
    val isLastPage: Boolean = false,
    val isLoadingMore: Boolean = false,
    val paramsAtLastFetch: EventQueryParams? = null
)

@HiltViewModel
class EventListViewModel @Inject constructor(
    private val repository: EventRepository,
    private val mainFeedRepository: MainFeedRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20L

        fun checkDate(eventStart: Long, filter: DateFilter, now: Long): Boolean {
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = now
            val currentDayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            
            val eventCalendar = java.util.Calendar.getInstance()
            eventCalendar.timeInMillis = eventStart
            val eventDay = eventCalendar.get(java.util.Calendar.DAY_OF_YEAR)
            val eventYear = eventCalendar.get(java.util.Calendar.YEAR)

            return when (filter) {
                DateFilter.ALL -> true
                DateFilter.TODAY -> eventYear == currentYear && eventDay == currentDayOfYear
                DateFilter.TOMORROW -> {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    val tomorrowDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
                    val tomorrowYear = calendar.get(java.util.Calendar.YEAR)
                    eventYear == tomorrowYear && eventDay == tomorrowDay
                }
                DateFilter.WEEKEND -> {
                    val dayOfWeek = eventCalendar.get(java.util.Calendar.DAY_OF_WEEK)
                    val isWeekendDay = dayOfWeek == java.util.Calendar.FRIDAY || 
                                       dayOfWeek == java.util.Calendar.SATURDAY || 
                                       dayOfWeek == java.util.Calendar.SUNDAY
                    val diff = eventStart - now
                    val maxDiff = 5 * 24 * 60 * 60 * 1000L
                    isWeekendDay && diff >= 0 && diff < maxDiff
                }
            }
        }
    }

    private val _queryParams = MutableStateFlow(EventQueryParams(geohash = null))
    val queryParams: StateFlow<EventQueryParams> = _queryParams.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    private val _searchRadiusKm = MutableStateFlow(AppConstants.DEFAULT_SEARCH_RADIUS_KM)
    val searchRadiusKm: StateFlow<Int> = _searchRadiusKm.asStateFlow()

    private val _userLocation = MutableStateFlow<ExactLocation?>(null)
    val userLocation: StateFlow<ExactLocation?> = _userLocation.asStateFlow()

    private val _paginationState = MutableStateFlow(PaginationState())

    private val _extraEvents = MutableStateFlow<List<Event>>(emptyList())
    val extraEvents: StateFlow<List<Event>> = _extraEvents.asStateFlow()

    private val _creatorProfilesCache = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

    private val queryTriggerFlow: StateFlow<EventQueryParams> = combine(
        _queryParams,
        _refreshTrigger
    ) { params: EventQueryParams, _: Int -> params }
        .distinctUntilChanged()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), EventQueryParams(geohash = null))

    private val eventsFlow: StateFlow<DataState<List<Event>>> = queryTriggerFlow
        .flatMapLatest { params: EventQueryParams ->
            flow {
                emit(DataState.Loading)
                try {
                    val result = mainFeedRepository.fetchEventsBatch(
                        geohashPrefix = params.geohash,
                        commune = null,
                        region = null,
                        limit = PAGE_SIZE,
                        lastSnapshot = null
                    )
                    result.onSuccess { (events, _) ->
                        emit(DataState.Success(events))
                    }.onFailure { error ->
                        emit(DataState.Error(error))
                    }
                } catch (e: Exception) {
                    emit(DataState.Error(e))
                }
            }.flowOn(Dispatchers.IO)
        }
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = DataState.Loading
        )

    private val finalEventsFlow: StateFlow<List<Event>> = combine(
        eventsFlow,
        _extraEvents
    ) { eventsState: DataState<List<Event>>, extraEvents: List<Event> ->
        val baseEvents = when (eventsState) {
            is DataState.Success -> eventsState.data
            else -> emptyList()
        }
        if (extraEvents.isEmpty()) baseEvents
        else (baseEvents + extraEvents).distinctBy { it.id }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val creatorProfilesFlow: StateFlow<Map<String, UserProfile>> = finalEventsFlow
        .mapLatest { events ->
            val creatorIds = events.map { it.creatorId }.distinct()
            val existingCache = _creatorProfilesCache.value
            val newIds = creatorIds.filter { it !in existingCache }
            
            if (newIds.isEmpty()) {
                existingCache
            } else {
                withContext(Dispatchers.IO) {
                    val newProfiles = newIds.mapNotNull { uid ->
                        userRepository.getUserProfile(uid)?.let { uid to it }
                    }.toMap()
                    val updatedCache = existingCache + newProfiles
                    _creatorProfilesCache.value = updatedCache
                    updatedCache
                }
            }
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyMap())

    private data class EventsData(
        val state: DataState<List<Event>>,
        val allEvents: List<Event>
    )

    private val eventsDataFlow: StateFlow<EventsData> = combine(eventsFlow, finalEventsFlow) { state, allEvents ->
        EventsData(state, allEvents)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), EventsData(DataState.Loading, emptyList()))

    private data class ProfilesData(
        val profiles: Map<String, UserProfile>,
        val pagination: PaginationState
    )

    private val profilesDataFlow: StateFlow<ProfilesData> = combine(creatorProfilesFlow, _paginationState) { profiles, pagination ->
        ProfilesData(profiles, pagination)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), ProfilesData(emptyMap(), PaginationState()))

    private data class LocationData(
        val params: EventQueryParams,
        val location: ExactLocation?
    )

    private val locationDataFlow: StateFlow<LocationData> = combine(_queryParams, _userLocation) { params, location ->
        LocationData(params, location)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), LocationData(EventQueryParams(geohash = null), null))

    private data class CombinedData(
        val eventsData: EventsData,
        val profilesData: ProfilesData,
        val locationData: LocationData
    )

    private val combinedDataFlow: StateFlow<CombinedData> = combine(
        combine(eventsDataFlow, profilesDataFlow) { ed, pd -> Pair(ed, pd) },
        locationDataFlow
    ) { pair: Pair<EventsData, ProfilesData>, ld: LocationData ->
        CombinedData(pair.first, pair.second, ld)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 
        CombinedData(EventsData(DataState.Loading, emptyList()), ProfilesData(emptyMap(), PaginationState()), LocationData(EventQueryParams(geohash = null), null)))

    val uiState: StateFlow<EventListUiState> = combinedDataFlow
        .mapLatest { data ->
            val eventsState = data.eventsData.state
            val allEvents = data.eventsData.allEvents
            val profiles = data.profilesData.profiles
            val pagination = data.profilesData.pagination
            val params = data.locationData.params
            val location = data.locationData.location

            val filteredEvents = applyClientFilters(allEvents, params, location)

            when (eventsState) {
                is DataState.Loading -> EventListUiState.Loading(params.category, params.dateFilter)
                is DataState.Error -> {
                    if (allEvents.isEmpty()) {
                        EventListUiState.Error(
                            eventsState.exception.message ?: "Error desconocido",
                            params.category,
                            params.dateFilter
                        )
                    } else {
                        EventListUiState.Success(
                            events = filteredEvents,
                            creatorProfiles = profiles,
                            currentUserLocation = location,
                            canLoadMore = !pagination.isLastPage,
                            isRefreshing = false,
                            selectedCategory = params.category,
                            selectedDateFilter = params.dateFilter
                        )
                    }
                }
                is DataState.Success -> EventListUiState.Success(
                    events = filteredEvents,
                    creatorProfiles = profiles,
                    currentUserLocation = location,
                    canLoadMore = !pagination.isLastPage,
                    isRefreshing = false,
                    selectedCategory = params.category,
                    selectedDateFilter = params.dateFilter
                )
            }
        }
        .distinctUntilChanged { old, new ->
            if (old is EventListUiState.Success && new is EventListUiState.Success) {
                old.events === new.events || (
                    old.events == new.events &&
                    old.selectedCategory == new.selectedCategory &&
                    old.selectedDateFilter == new.selectedDateFilter &&
                    old.isRefreshing == new.isRefreshing &&
                    old.canLoadMore == new.canLoadMore &&
                    old.currentUserLocation == new.currentUserLocation &&
                    old.creatorProfiles == new.creatorProfiles
                )
            } else {
                old == new
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = EventListUiState.Loading()
        )

    val isLoadingMore: StateFlow<Boolean> = _paginationState
        .mapLatest { it.isLoadingMore }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

    fun selectCategory(type: EventType?) {
        _queryParams.update { 
            if (it.category == type) it.copy(category = null) 
            else it.copy(category = type) 
        }
        resetPagination()
    }

    fun selectDateFilter(filter: DateFilter) {
        _queryParams.update { it.copy(dateFilter = filter) }
        resetPagination()
    }

    fun updateRadius(radius: Int) {
        if (_searchRadiusKm.value != radius) {
            _searchRadiusKm.value = radius
            _userLocation.value?.let { loc ->
                val precision = GeohashUtils.getPrecisionForRadius(radius)
                val geohash = GeohashUtils.encode(loc.latitude, loc.longitude, precision)
                _queryParams.update { it.copy(geohash = geohash, radiusKm = radius) }
            }
            resetPagination()
        }
    }

    fun updateLocation(lat: Double, lng: Double) {
        val loc = ExactLocation(lat, lng, "")
        _userLocation.value = loc
        val radius = _searchRadiusKm.value
        val precision = GeohashUtils.getPrecisionForRadius(radius)
        val geohash = GeohashUtils.encode(lat, lng, precision)
        _queryParams.update { it.copy(geohash = geohash) }
        resetPagination()
    }

    fun searchNearLocation(lat: Double, lng: Double, radiusKm: Int = _searchRadiusKm.value) {
        _searchRadiusKm.value = radiusKm
        val precision = GeohashUtils.getPrecisionForRadius(radiusKm)
        val geohash = GeohashUtils.encode(lat, lng, precision)
        _userLocation.value = ExactLocation(lat, lng, "")
        _queryParams.update { it.copy(geohash = geohash, radiusKm = radiusKm) }
        resetPagination()
    }

    fun searchAllLocations() {
        _queryParams.update { it.copy(geohash = null) }
        resetPagination()
    }

    fun searchAllRegions() = searchAllLocations()

    fun updateCommune(commune: String?) {
        android.util.Log.d("EventListViewModel", "updateCommune ignorado (modo global): $commune")
    }

    fun updateRegion(region: String?) {
        android.util.Log.d("EventListViewModel", "updateRegion ignorado (modo global): $region")
    }

    fun loadMore() {
        val state = _paginationState.value
        if (state.isLoadingMore || state.isLastPage) return
        
        val currentParams = _queryParams.value
        if (state.paramsAtLastFetch != null && state.paramsAtLastFetch != currentParams) {
            resetPagination()
            return
        }
        
        _paginationState.value = state.copy(isLoadingMore = true)
        
        viewModelScope.launch {
            try {
                val result = mainFeedRepository.fetchEventsBatch(
                    geohashPrefix = currentParams.geohash,
                    commune = null,
                    region = null,
                    limit = PAGE_SIZE,
                    lastSnapshot = state.lastVisibleDoc
                )
                
                if (_queryParams.value != currentParams) {
                    _paginationState.value = state.copy(isLoadingMore = false)
                    return@launch
                }
                
                result.onSuccess { (newEvents, newLastDoc) ->
                    if (newEvents.isEmpty()) {
                        _paginationState.value = state.copy(
                            isLastPage = true,
                            isLoadingMore = false
                        )
                        return@onSuccess
                    }
                    
                    _extraEvents.update { current ->
                        (current + newEvents).distinctBy { it.id }
                    }
                    
                    val canLoadMore = newEvents.size.toLong() >= PAGE_SIZE
                    _paginationState.value = state.copy(
                        lastVisibleDoc = newLastDoc,
                        isLastPage = !canLoadMore,
                        isLoadingMore = false,
                        paramsAtLastFetch = currentParams
                    )
                }.onFailure { error ->
                    android.util.Log.e("EventListViewModel", "Error loading more: ${error.message}")
                    _paginationState.value = state.copy(isLoadingMore = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("EventListViewModel", "Error loading more: ${e.message}")
                _paginationState.value = state.copy(isLoadingMore = false)
            }
        }
    }

    fun refresh() {
        _extraEvents.value = emptyList()
        resetPagination()
        _refreshTrigger.update { it + 1 }
    }

    init {
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

    private fun resetPagination() {
        _extraEvents.value = emptyList()
        _paginationState.value = PaginationState()
    }

    private fun applyClientFilters(
        events: List<Event>,
        params: EventQueryParams,
        location: ExactLocation?
    ): List<Event> {
        val now = System.currentTimeMillis()
        val maxRadiusMeters = params.radiusKm * 1000f
        
        return events.asSequence().filter { event ->
            val isNear = if (location != null && event.latitude != null && event.longitude != null) {
                try {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        location.latitude, location.longitude,
                        event.latitude, event.longitude,
                        results
                    )
                    results[0] <= maxRadiusMeters
                } catch (e: Exception) { true }
            } else true

            val matchesCategory = params.category == null || event.eventType == params.category
            val matchesDate = checkDate(event.startAt, params.dateFilter, now)
            val isNotClosed = event.status == EventStatus.OPEN
            val isNotExpired = event.endAt > now

            isNear && matchesCategory && matchesDate && isNotClosed && isNotExpired
        }.toList()
    }
}