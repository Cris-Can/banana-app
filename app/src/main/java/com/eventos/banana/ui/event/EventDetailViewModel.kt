package com.eventos.banana.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.domain.model.EventDetailUiState
import com.eventos.banana.domain.model.JoinRequest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.data.repository.RatingRepository

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import com.eventos.banana.data.repository.SubscriptionRepository
import com.eventos.banana.data.repository.EncounterRepository

@HiltViewModel(assistedFactory = EventDetailViewModel.Factory::class)
class EventDetailViewModel @AssistedInject constructor(
    @Assisted private val eventId: String,
    private val repository: EventRepository,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val encounterRepository: EncounterRepository,
    private val ratingRepository: RatingRepository,
    private val processCheckInUseCase: com.eventos.banana.domain.usecase.event.ProcessCheckInUseCase
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(eventId: String): EventDetailViewModel
    }

    private val _uiState =
        MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)
    val uiState: StateFlow<EventDetailUiState> = _uiState

    // 🆕 Track join submission state for UI feedback
    private val _joinSubmissionState = MutableStateFlow<JoinSubmissionState>(JoinSubmissionState.Idle)
    val joinSubmissionState: StateFlow<JoinSubmissionState> = _joinSubmissionState

    // 🆕 Track approval/rejection results
    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState

    // 📺 AD STATE
    sealed class UnlockState {
        object Idle : UnlockState()
        object LoadingAd : UnlockState()
        data class Progress(val watched: Int, val required: Int = 2) : UnlockState()
        object Unlocked : UnlockState()
        data class Error(val message: String) : UnlockState()
    }

    private val _adUnlockState = MutableStateFlow<UnlockState>(UnlockState.Idle)
    val adUnlockState: StateFlow<UnlockState> = _adUnlockState

    // 🔔 New-request alert: emitted once per batch of new pending requests
    private val _newRequestAlert = MutableStateFlow<String?>(null)
    val newRequestAlert: StateFlow<String?> = _newRequestAlert

    // Tracks last known pending count to detect growth
    private var lastKnownPendingCount = -1 // -1 = not yet initialized

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
                            // 🛡️ SANITIZE DATA: Filter out potential nulls from Firestore lists (Ghost data)
                            @Suppress("UNCHECKED_CAST")
                            val safePending = (event.pendingRequests as? List<JoinRequest?>)?.filterNotNull() ?: emptyList()
                            @Suppress("UNCHECKED_CAST")
                            val safeApproved = (event.approvedParticipants as? List<String?>)?.filterNotNull() ?: emptyList()
                            @Suppress("UNCHECKED_CAST")
                            val safeRejected = (event.rejectedParticipants as? List<String?>)?.filterNotNull() ?: emptyList()
                            
                            val sanitizedEvent = event.copy(
                                pendingRequests = safePending,
                                approvedParticipants = safeApproved,
                                rejectedParticipants = safeRejected
                            )

                            // 1. Emit event immediately (Sanitized)
                            val currentState = _uiState.value
                            val currentProfiles = (currentState as? EventDetailUiState.Success)?.userProfiles ?: emptyMap()
                            _uiState.value = EventDetailUiState.Success(sanitizedEvent, userProfiles = currentProfiles)

                            // 🔔 Detect new pending requests (only after first load)
                            val newPendingCount = safePending.size
                            if (lastKnownPendingCount >= 0 && newPendingCount > lastKnownPendingCount) {
                                val delta = newPendingCount - lastKnownPendingCount
                                val alertMsg = if (delta == 1) "Nueva solicitud de unirse" else "$delta nuevas solicitudes de unirse"
                                _newRequestAlert.value = alertMsg
                            }
                            lastKnownPendingCount = newPendingCount
        
                            // 2. Batch fetch profiles for creator + participants + APPLICANTS
                            val applicants = sanitizedEvent.pendingRequests.map { it.userId }
                            val userIdsToFetch = (sanitizedEvent.approvedParticipants + listOf(sanitizedEvent.creatorId) + applicants).distinct()

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

                            _uiState.value = EventDetailUiState.Success(sanitizedEvent, userProfiles = newProfiles)
                            
                        } catch (e: Exception) {
                            android.util.Log.e("EventDetailVM", "Failed batch fetch", e)
                        }
                    }
                }
        }
    }

    // ---------- SOLICITUDES ----------
    fun approveParticipant(participantId: String) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            val result = repository.approveParticipant(eventId, participantId)
            if (result.isSuccess) {
                 // 📊 Stats: Cloud Function "onParticipantApproved" se encarga automáticamente
                 _actionState.value = ActionState.Success("Participante aprobado")
            } else {
                 _actionState.value = ActionState.Error(result.exceptionOrNull()?.message ?: "Error al aprobar")
            }
        }
    }

    fun rejectParticipant(participantId: String) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            val result = repository.rejectParticipant(eventId, participantId)
            if (result.isSuccess) {
                _actionState.value = ActionState.Success("Participante rechazado")
            } else {
                _actionState.value = ActionState.Error(result.exceptionOrNull()?.message ?: "Error al rechazar")
            }
        }
    }
    
    fun resetActionState() {
        _actionState.value = ActionState.Idle
    }

    fun resetNewRequestAlert() {
        _newRequestAlert.value = null
    }



    fun requestJoinEventWithAnswers(
        userId: String,
        answers: Map<String, String>
    ) {
        viewModelScope.launch {
            _joinSubmissionState.value = JoinSubmissionState.Loading
            
            // 🚨 Check if event is PUBLIC first to skip Limits?
            // User requested that Public events be "unrestricted".
            // However, premium/free limits on "Joins" might still apply?
            // "Sin restricción de usuarios" usually means approval restrictions.
            // Let's keep the join count limit (3/month) for now unless strictly told otherwise.
            
            // Check Limits
            val canJoin = subscriptionRepository.canJoinEvent(userId)
            if (canJoin.isFailure || !canJoin.getOrDefault(false)) {
                _joinSubmissionState.value = JoinSubmissionState.Error("LIMIT_REACHED")
                return@launch
            }

            try {
                // Fetch event to check if public
                // Ideally this info is passed in, but we can trust the repo or current state
                val currentEvent = (_uiState.value as? EventDetailUiState.Success)?.event
                
                if (currentEvent?.isPublic == true) {
                     // 🌍 DIRECT JOIN
                     val result = repository.joinEventDirectly(eventId, userId)
                     if (result.isSuccess) {
                         // Increment Usage (Subscription limits still apply?)
                         subscriptionRepository.incrementJoinCount(userId)
                         
                         // 📊 STATS: DO NOT INCREMENT requested count for Public Events
                         // This ensures reliability score isn't affected.
                         
                         _joinSubmissionState.value = JoinSubmissionState.Success
                     } else {
                         _joinSubmissionState.value = JoinSubmissionState.Error(result.exceptionOrNull()?.message ?: "Error")
                     }
                } else {
                    // 🔒 RESTRICTED JOIN (Normal)
                    val userNickname = userRepository.getUserProfile(userId)?.nickname ?: "Usuario"
                    val answersWithNickname = answers + ("_nickname" to userNickname)
    
                    val result = repository.requestJoinEventWithAnswers(
                        eventId = eventId,
                        userId = userId,
                        answers = answersWithNickname
                    )
    
                    if (result.isSuccess) {
                        subscriptionRepository.incrementJoinCount(userId)
                         // 📊 STATS: DO NOT INCREMENT requested count on Request.
                         // We wait for Approval in restricted events too (changed recently).
                        _joinSubmissionState.value = JoinSubmissionState.Success
                    } else {
                        _joinSubmissionState.value = JoinSubmissionState.Error(
                            result.exceptionOrNull()?.message ?: "Error desconocido"
                        )
                    }
                }
            } catch (e: Exception) {
                _joinSubmissionState.value = JoinSubmissionState.Error(e.message ?: "Excepción inesperada")
            }
        }
    }

    fun resetJoinSubmissionState() {
        _joinSubmissionState.value = JoinSubmissionState.Idle
        _adUnlockState.value = UnlockState.Idle
    }

    fun resetAdUnlockState() {
        _adUnlockState.value = UnlockState.Idle
    }

    // ---------- ADS LOGIC ----------
    fun watchAd(activity: android.app.Activity, userId: String) {
        _adUnlockState.value = UnlockState.LoadingAd
        
        com.eventos.banana.util.AdMobHelper.loadRewardedAd(activity)
        
        com.eventos.banana.util.AdMobHelper.showRewardedAd(
            activity = activity,
            onUserEarnedReward = {
                viewModelScope.launch {
                    val result = subscriptionRepository.recordAdWatch(userId)
                    if (result.isSuccess) {
                        val (unlocked, progress) = result.getOrThrow()
                        if (progress == 0) { // Reset means we looped -> Unlocked!
                            _adUnlockState.value = UnlockState.Unlocked
                        } else {
                            _adUnlockState.value = UnlockState.Progress(progress, 2)
                        }
                    } else {
                        _adUnlockState.value = UnlockState.Error("Error guardando progreso: ${result.exceptionOrNull()?.message}")
                    }
                }
            },
            onAdDismissed = {
                 if (_adUnlockState.value == UnlockState.LoadingAd) {
                     _adUnlockState.value = UnlockState.Idle // User closed without watching
                 }
            }
        )
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
            // We only check technical participation (GPS) here. 
            // Creator status is checked in UI or combined later.
            _hasAttended.value = encounterRepository.hasAttended(eventId, userId, isCreator = false)
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
                val result = processCheckInUseCase(eventId, userId)
                
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

    fun boostWithCredit(currentUserId: String) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            val result = ratingRepository.spendCredit(currentUserId)
            if (result.isSuccess) {
                repository.boostEvent(eventId, 24 * 60 * 60 * 1000L)
                loadEvent()
                _actionState.value = ActionState.Success("✅ Evento destacado con 1 crédito")
            } else {
                _actionState.value = ActionState.Error("❌ No tienes créditos suficientes")
            }
        }
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
sealed interface ActionState {
    object Idle : ActionState
    object Loading : ActionState
    data class Success(val message: String) : ActionState
    data class Error(val message: String) : ActionState
}
