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
                    val currentNicknames = (_uiState.value as? EventDetailUiState.Success)?.userNicknames ?: emptyMap()
                    _uiState.value = EventDetailUiState.Success(event, userNicknames = currentNicknames)

                    // 2. Fetch nicknames for creator + participants + APPLICANTS
                    val applicants = event.pendingRequests.map { it.userId }
                    val userIdsToFetch = (event.approvedParticipants + event.creatorId + applicants).distinct()
                    android.util.Log.d("EventDetailVM", "Fetching nicknames for users (incl. applicants): $userIdsToFetch")
                    
                    val newNicknames = currentNicknames.toMutableMap()
                    var hasUpdates = false

                    userIdsToFetch.forEach { uid ->
                        // 🔄 REFRESH: If nickname is missing OR is "Usuario" (ghost), try to fetch fresh data
                        val needsRefresh = !newNicknames.containsKey(uid) || newNicknames[uid] == "Usuario"
                        
                        if (needsRefresh) {
                            try {
                                // ⚡ FORCE REFRESH: Ignore cache to get real nickname
                                val profile = userRepository.getUserProfile(uid, forceRefresh = true)
                                if (profile != null) {
                                    newNicknames[uid] = profile.nickname
                                    hasUpdates = true
                                    android.util.Log.d("EventDetailVM", "Loaded/Refreshed nickname for $uid: ${profile.nickname}")
                                } else {
                                    // Only set fallback if we don't have one or if we are refreshing
                                    if (!newNicknames.containsKey(uid)) {
                                        newNicknames[uid] = "Usuario"
                                        hasUpdates = true
                                        android.util.Log.w("EventDetailVM", "Profile not found for $uid, using fallback")
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("EventDetailVM", "Failed to load nickname for $uid", e)
                                if (!newNicknames.containsKey(uid)) {
                                    newNicknames[uid] = "Usuario"
                                    hasUpdates = true
                                }
                            }
                        }
                    }

                    if (hasUpdates) {
                        // ... same as before
                        android.util.Log.d("EventDetailVM", "Updating state with nicknames: $newNicknames")
                        _uiState.value = EventDetailUiState.Success(
                            event = event,
                            userNicknames = newNicknames
                        )
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
}

sealed interface JoinSubmissionState {
    object Idle : JoinSubmissionState
    object Loading : JoinSubmissionState
    object Success : JoinSubmissionState
    data class Error(val message: String) : JoinSubmissionState
}
