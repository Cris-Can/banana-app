package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.domain.model.EventDetailUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.eventos.banana.data.repository.UserRepository

class EventDetailViewModel(
    private val eventId: String,
    private val repository: EventRepository = EventRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)
    val uiState: StateFlow<EventDetailUiState> = _uiState

    // 🆕 Track join submission state for UI feedback
    private val _joinSubmissionState = MutableStateFlow<JoinSubmissionState>(JoinSubmissionState.Idle)
    val joinSubmissionState: StateFlow<JoinSubmissionState> = _joinSubmissionState

    init {
        loadEvent()
    }

    private fun loadEvent() {
        viewModelScope.launch {
            repository.listenToEvent(eventId)
                .catch { e ->
                    _uiState.value = EventDetailUiState.Error(
                        e.message ?: "Error al observar el evento"
                    )
                }
                .collect { event ->
                    // 1. Emit event immediately
                    val currentProfiles = (_uiState.value as? EventDetailUiState.Success)?.userProfiles ?: emptyMap()
                    _uiState.value = EventDetailUiState.Success(event, userProfiles = currentProfiles)

                    // 2. Batch fetch profiles for creator + participants + APPLICANTS
                    val applicants = event.pendingRequests.map { it.userId }
                    val userIdsToFetch = (event.approvedParticipants + listOf(event.creatorId) + applicants).distinct()

                    // Filter IDs that are missing or are just placeholders "Usuario"
                    val missingIds = userIdsToFetch.filter { uid ->
                        !currentProfiles.containsKey(uid) || currentProfiles[uid]?.nickname == "Usuario"
                    }
                    
                    if (missingIds.isNotEmpty()) {
                        android.util.Log.d("EventDetailVM", "Batch fetching ${missingIds.size} users: $missingIds")
                        try {
                            // ⚡ BATCH FETCH
                            val fetchedList = userRepository.getUsers(missingIds)
                            
                            // Merge into map
                            val newProfiles = currentProfiles.toMutableMap()
                            newProfiles.putAll(fetchedList.associateBy { it.uid })
                            
                            // Better logic for fallback:
                            missingIds.forEach { reqUid ->
                                // If still missing or dummy (and not updated by fetch), set dummy
                                val profile = newProfiles[reqUid]
                                if (profile == null || (profile.nickname == "Usuario" && fetchedList.none { it.uid == reqUid })) {
                                    newProfiles[reqUid] = com.eventos.banana.domain.model.UserProfile(uid = reqUid, nickname = "Usuario")
                                }
                            }

                            _uiState.value = EventDetailUiState.Success(event, userProfiles = newProfiles)
                            
                        } catch (e: Exception) {
                            android.util.Log.e("EventDetailVM", "Failed batch fetch", e)
                        }
                    }
                }
        }
    }

    // ---------- SOLICITUDES ----------
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

    private val subscriptionRepository = com.eventos.banana.data.repository.SubscriptionRepository()

    fun requestJoinEventWithAnswers(
        userId: String,
        answers: Map<String, String>
    ) {
        viewModelScope.launch {
            _joinSubmissionState.value = JoinSubmissionState.Loading
            
            // Check Limits
            val canJoin = subscriptionRepository.canJoinEvent(userId)
            if (canJoin.isFailure || !canJoin.getOrDefault(false)) {
                _joinSubmissionState.value = JoinSubmissionState.Error(
                    "Has alcanzado tu límite mensual de solicitudes (3/mes). ¡Mejórate a Premium!"
                )
                return@launch
            }

            try {
                val userNickname = userRepository.getUserProfile(userId)?.nickname ?: "Usuario"
                val answersWithNickname = answers + ("_nickname" to userNickname)

                val result = repository.requestJoinEventWithAnswers(
                    eventId = eventId,
                    userId = userId,
                    answers = answersWithNickname
                )

                if (result.isSuccess) {
                    // Increment Usage
                    subscriptionRepository.incrementJoinCount(userId)
                    
                    // 📊 STATS (Round 14)
                    userRepository.incrementEventsRequested(userId)
                    
                    _joinSubmissionState.value = JoinSubmissionState.Success
                } else {
                    _joinSubmissionState.value = JoinSubmissionState.Error(
                        result.exceptionOrNull()?.message ?: "Error desconocido"
                    )
                }
            } catch (e: Exception) {
                _joinSubmissionState.value = JoinSubmissionState.Error(e.message ?: "Excepción inesperada")
            }
        }
    }

    fun resetJoinSubmissionState() {
        _joinSubmissionState.value = JoinSubmissionState.Idle
    }

    // ---------- A15.1 MODERACIÓN ----------
    fun cancelEvent(reason: String) {
        viewModelScope.launch {
            repository.cancelEvent(eventId, reason)
        }
    }

    fun closeEvent() {
        viewModelScope.launch {
            repository.closeEvent(eventId)
        }
    }

    fun removeParticipant(userId: String) {
        viewModelScope.launch {
            repository.removeParticipant(eventId, userId)
        }
    }

    fun deleteEvent() {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
        }
    }

    // 💾 SAVE & ATTENDANCE (A30)
    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved

    private val _hasAttended = MutableStateFlow(false)
    val hasAttended: StateFlow<Boolean> = _hasAttended

    // 🆕 Check-in State for UI loading/error
    private val _checkInState = MutableStateFlow<CheckInState>(CheckInState.Idle)
    val checkInState: StateFlow<CheckInState> = _checkInState

    fun loadUserInteractionState(userId: String) {
        viewModelScope.launch {
            // Saved State
            val profile = userRepository.getUserProfile(userId)
            _isSaved.value = profile?.savedEventIds?.contains(eventId) == true

            // Attendance State
            val encounterRepo = com.eventos.banana.data.repository.EncounterRepository()
            // We only check technical participation (GPS) here. 
            // Creator status is checked in UI or combined later.
            _hasAttended.value = encounterRepo.hasAttended(eventId, userId, isCreator = false)
        }
    }

    fun toggleSaveEvent(userId: String) {
        val current = _isSaved.value
        viewModelScope.launch {
            userRepository.toggleEventSaved(userId, eventId, !current)
            _isSaved.value = !current
        }
    }

    fun performCheckIn(userId: String) {
        viewModelScope.launch {
            _checkInState.value = CheckInState.Loading
            try {
                val encounterRepo = com.eventos.banana.data.repository.EncounterRepository()
                val result = encounterRepo.recordCheckIn(eventId, userId)
                
                if (result.isSuccess) {
                    _hasAttended.value = true
                    _checkInState.value = CheckInState.Success
                } else {
                    _checkInState.value = CheckInState.Error(result.exceptionOrNull()?.message ?: "Error desconocido")
                }
            } catch (e: Exception) {
                _checkInState.value = CheckInState.Error(e.message ?: "Error inesperado")
            }
        }
    }
    
    fun resetCheckInState() {
        _checkInState.value = CheckInState.Idle
    }
}

sealed interface JoinSubmissionState {
    object Idle : JoinSubmissionState
    object Loading : JoinSubmissionState
    object Success : JoinSubmissionState
    data class Error(val message: String) : JoinSubmissionState
}

sealed interface CheckInState {
    object Idle : CheckInState
    object Loading : CheckInState
    object Success : CheckInState
    data class Error(val message: String) : CheckInState
}
