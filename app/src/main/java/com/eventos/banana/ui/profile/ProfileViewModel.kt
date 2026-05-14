package com.eventos.banana.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.remote.model.EventDto
import com.eventos.banana.data.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    object Success : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: com.eventos.banana.data.repository.AuthRepository,
    private val eventRepository: com.eventos.banana.data.repository.EventRepository,
    private val uploadProfilePhotoUseCase: com.eventos.banana.domain.usecase.profile.UploadProfilePhotoUseCase,
    private val manageFriendsUseCase: com.eventos.banana.domain.usecase.profile.ManageFriendsUseCase,
    private val updateProfileSettingsUseCase: com.eventos.banana.domain.usecase.profile.UpdateProfileSettingsUseCase,
    private val createUserProfileUseCase: com.eventos.banana.domain.usecase.profile.CreateUserProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState

    // 📸 Upload progress tracking
    private val _isUploadingPhoto = MutableStateFlow(false)
    val isUploadingPhoto: StateFlow<Boolean> = _isUploadingPhoto.asStateFlow()


    fun updateNickname(uid: String, nickname: String) {
        android.util.Log.d("ProfileViewModel", "updateNickname called: uid='$uid', nickname='$nickname'")
        
        if (uid.isBlank()) {
            android.util.Log.e("ProfileViewModel", "UID is blank!")
            _uiState.value = ProfileUiState.Error("Error interno: UID vacío")
            return
        }
        
        if (nickname.isBlank()) {
            _uiState.value = ProfileUiState.Error("El nombre no puede estar vacío")
            return
        }

        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                userRepository.updateNickname(uid, nickname.trim())
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "updateNickname failed", e)
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }

    fun updateLocation(uid: String, region: String, commune: String, country: String? = null) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val result = updateProfileSettingsUseCase.updateLocation(uid, region, commune, country)
            if (result.isSuccess) {
                _uiState.value = ProfileUiState.Success
            } else {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(result.exceptionOrNull()))
            }
        }
    }

    fun updateNotifyEventsByCommune(uid: String, enabled: Boolean, region: String?, commune: String?) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val result = updateProfileSettingsUseCase.updateNotifyEventsByCommune(uid, enabled, region, commune)
            if (result.isSuccess) {
                _uiState.value = ProfileUiState.Success
            } else {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(result.exceptionOrNull()))
            }
        }
    }

    fun updateNotifyEventWall(uid: String, enabled: Boolean) {
        viewModelScope.launch {
            // Optimistic update mechanism could be better, but standard for now
            try {
                userRepository.updateNotifyEventWall(uid, enabled)
                // Success - listener will update UI
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }

    // 🔔 A29 — CATEGORY SUBSCRIPTION
    fun toggleCategorySubscription(uid: String, categoryTopic: String, isEnabled: Boolean) {
        viewModelScope.launch { 
            val result = updateProfileSettingsUseCase.toggleCategorySubscription(uid, categoryTopic, isEnabled)
            if (result.isFailure) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(result.exceptionOrNull()))
            }
        }
    }

    fun updateLocationFromDevice(
        uid: String,
        region: String,
        commune: String,
        country: String,
        lat: Double,
        lng: Double
    ) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val result = updateProfileSettingsUseCase.updateLocationFromDevice(uid, region, commune, country, lat, lng)
            if (result.isSuccess) {
                _uiState.value = ProfileUiState.Success
            } else {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(result.exceptionOrNull()))
            }
        }
    }


    // =====================================================
    // 🎨 A20 — SOCIAL PROFILE
    // =====================================================
    fun updateSocialProfile(uid: String, aboutMe: String, interests: List<String>) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                // 1. Calculate Auto-Subscriptions based on Interests
                // Logic: If user selects "Gym" (subcategory of DEPORTES), auto-subscribe to "events_DEPORTES"
                val allEventTypes = com.eventos.banana.domain.model.EventType.values()
                val detectedCategories = mutableSetOf<String>()
                
                // Get current profile to merge, not overwrite existing manually enabled subscriptions if possible
                // For MVP, we'll calculate fresh from interests + existing
                val currentProfile = userRepository.getUserProfile(uid)
                val currentSubscriptions = currentProfile?.subscribedCategories?.toMutableSet() ?: mutableSetOf()
                
                allEventTypes.forEach { type ->
                    // Check if ANY of the user's interests matches a subcategory of this type
                    // Case-insensitive check recommended
                    val matches = type.subcategories.any { sub -> 
                        interests.any { it.equals(sub, ignoreCase = true) }
                    }
                    
                    if (matches) {
                         detectedCategories.add("events_${type.name}")
                    }
                }
                
                // Merge: Add detected ones to existing
                currentSubscriptions.addAll(detectedCategories)
                
                // 2. Update Profile (Social Info)
                userRepository.updateSocialProfile(uid, aboutMe, interests)
                
                // 3. Update Subscriptions (If changed)
                if (currentSubscriptions.isNotEmpty()) {
                    userRepository.updateSubscribedCategories(uid, currentSubscriptions.toList())
                }
                
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }

    fun updateAppTheme(uid: String, theme: String) {
        viewModelScope.launch {
            // Don't block UI with Loading state for theme switch (instant feedback preferred), 
            // the SessionVM listener will update the global state anyway.
            val result = updateProfileSettingsUseCase.updateAppTheme(uid, theme)
            if (result.isFailure) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(result.exceptionOrNull()))
            }
        }
    }

    fun uploadPhoto(uid: String, imageBytes: ByteArray?, isProfilePicture: Boolean = false, isCoverPhoto: Boolean = false) {
        if (imageBytes == null) return
        
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            _isUploadingPhoto.value = true
            try {
                val result = uploadProfilePhotoUseCase(uid, imageBytes, isProfilePicture, isCoverPhoto)
                
                if (result.isSuccess) {
                    android.util.Log.d("ProfileViewModel", "✅ Foto subida OK")
                    _uiState.value = ProfileUiState.Success
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Error desconocido al subir foto"
                    android.util.Log.e("ProfileViewModel", "❌ Error subiendo foto: $errorMsg")
                    _uiState.value = ProfileUiState.Error(errorMsg)
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "❌ Exception en uploadPhoto", e)
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            } finally {
                _isUploadingPhoto.value = false
            }
        }
    }

    // =====================================================
    // 🤝 A20 — FRIENDS
    // =====================================================
    fun sendFriendRequest(targetUid: String) {
        viewModelScope.launch {
            val result = manageFriendsUseCase.sendFriendRequest(targetUid)
            if (result.isSuccess) {
                _uiState.value = ProfileUiState.Success
            } else {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(result.exceptionOrNull()))
            }
        }
    }

    fun acceptFriendRequest(requesterUid: String) {
        viewModelScope.launch {
            val result = manageFriendsUseCase.acceptFriendRequest(requesterUid)
            if (result.isSuccess) {
                _uiState.value = ProfileUiState.Success
            } else {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(result.exceptionOrNull()))
            }
        }
    }

    fun deletePhoto(uid: String, photoUrl: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                userRepository.deletePhoto(uid, photoUrl)
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }


    // 🔐 PASSWORD RESET
    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val result = authRepository.sendPasswordResetEmail(email)
            if (result.isSuccess) {
                _uiState.value = ProfileUiState.Success
            } else {
                val error = result.exceptionOrNull()
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(error))
            }
        }
    }

    // 💾 HISTORY & SAVED EVENTS
    private val _historyEvents = MutableStateFlow<List<com.eventos.banana.domain.model.Event>>(emptyList())
    val historyEvents: StateFlow<List<com.eventos.banana.domain.model.Event>> = _historyEvents

    private val _savedEvents = MutableStateFlow<List<com.eventos.banana.domain.model.Event>>(emptyList())
    val savedEvents: StateFlow<List<com.eventos.banana.domain.model.Event>> = _savedEvents

    private val _debugStatus = MutableStateFlow<String>("Esperando carga...")
    val debugStatus: StateFlow<String> = _debugStatus

    fun loadUserEvents(uid: String, savedEventIds: List<String>) {
        viewModelScope.launch {
            // Obtener email para debug
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val authEmail = currentUser?.email ?: "No Auth Email"
            var firestoreProfile = userRepository.getUserProfile(uid)
            val firestoreEmail = firestoreProfile?.email ?: "No FS Profile"
            
            // 🔧 Removed AUTO-REPAIR mechanism to reduce latency. Missing profiles should be handled during Login/Registration.
            _debugStatus.value = "Cargando... \nUID: $uid\nAuth: $authEmail\nFS: $firestoreEmail"

            try {
                // OPTIMIZATION: Use DEFAULT (cache-first, server fallback) for faster loads
                val source = com.google.firebase.firestore.Source.DEFAULT
                
                // PARALLEL FETCH
                val createdEventsJob = async {
                    try {
                        eventRepository.getEventsByCreatorId(uid, source)
                    } catch (e: Exception) { 
                        _debugStatus.value = "Error Created: ${e.message}"
                        emptyList() 
                    }
                }
                
                val participatedEventsJob = async {
                    try {
                        eventRepository.getEventsByParticipantId(uid, source)
                    } catch (e: Exception) { 
                        _debugStatus.value = "Error Participated: ${e.message}"
                        emptyList() 
                    }
                }

                val savedEventsJob = async {
                    try {
                        eventRepository.getEventsByIds(savedEventIds, source)
                    } catch (e: Exception) { 
                        emptyList() 
                    }
                }

                val created: List<com.eventos.banana.domain.model.Event> = createdEventsJob.await()
                val participated: List<com.eventos.banana.domain.model.Event> = participatedEventsJob.await()
                
                val saved: List<com.eventos.banana.domain.model.Event> = savedEventsJob.await()
                
                // Merge History (Created + Participated)
                val allHistoryCandidates = (created + participated).distinctBy { it.id }
                
                val now = System.currentTimeMillis()
                val oneWeekMillis = 7 * 24 * 60 * 60 * 1000L
                val oneWeekAgo = now - oneWeekMillis
                
                // HISTORY FILTER: Retention Limit (1 Week) unless Saved
                val finalEvents = allHistoryCandidates.filter { event ->
                    val effectiveEndAt = if (event.endAt > 0) event.endAt else (event.startAt + 14400000L)
                    val isPast = effectiveEndAt < now || event.status == com.eventos.banana.domain.model.EventStatus.CLOSED || event.status == com.eventos.banana.domain.model.EventStatus.CANCELLED
                    
                    val isRecent = effectiveEndAt > oneWeekAgo
                    val isSaved = savedEventIds.contains(event.id)
                    
                    // Show if Past AND (Recent OR Saved)
                    isPast && (isRecent || isSaved)
                }.sortedByDescending { it.endAt }

                _historyEvents.value = finalEvents
                
                val warning = if (finalEvents.isEmpty() && firestoreEmail == "No FS Profile") 
                    "\n⚠️ CUENTA RECREADA. Historial antiguo perdido (nuevo UID)." 
                    else ""
                
                _debugStatus.value = "UID: $uid\nAuth: $authEmail\nFS: ${firestoreProfile?.email ?: "Recién Creado"}\nEvts: ${finalEvents.size}$warning"

                // SAVED FILTER
                _savedEvents.value = saved.sortedBy { it.startAt } // Upcoming first

            } catch (e: Exception) {
                _debugStatus.value = "Error Fatal: ${e.message}"
                android.util.Log.e("ProfileViewModel", "Error loading user events", e)
            }
        }
    }

    fun toggleSaveEvent(uid: String, eventId: String, currentSavedIds: List<String>) {
        val isSaved = currentSavedIds.contains(eventId)
        viewModelScope.launch {
            try {
                userRepository.toggleEventSaved(uid, eventId, !isSaved)
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }



    private fun getFriendlyErrorMessage(e: Throwable?): String {
        if (e == null) return "Error desconocido"
        val msg = e.message ?: ""
        return when {
            msg.contains("403") || msg.contains("App attestation") -> 
                "⛔ Bloqueo de seguridad (App Check). Registra el Token en Firebase."
            msg.contains("PERMISSION_DENIED") -> 
                "⛔ Permisos insuficientes (Firestore Rules)."
            // Detección de problemas de DNS
            msg.contains("grandle") || msg.contains("UnknownHostException") || msg.contains("EAI_NODATA") || msg.contains("No address associated") -> 
                "📡 Error de DNS/Red. Tu dispositivo tiene internet pero no resuelve direcciones. Prueba reiniciar WiFi o usar Datos."
            else -> "Error: ${msg.take(50)}..."
        }
    }

    // 👁️ WHO VIEWED MY PROFILE (Round 48)
    data class ProfileViewItem(
        val user: com.eventos.banana.domain.model.UserProfile,
        val timestamp: Long
    )

    private val _profileViews = MutableStateFlow<List<ProfileViewItem>>(emptyList())
    val profileViews: StateFlow<List<ProfileViewItem>> = _profileViews

    fun loadProfileViews(uid: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val rawViews = userRepository.getProfileViews(uid)
                if (rawViews.isEmpty()) {
                    _profileViews.value = emptyList()
                    _uiState.value = ProfileUiState.Success
                    return@launch
                }

                val userIds = rawViews.map { it.visitorUid }
                val users = userRepository.getUsers(userIds)
                
                val items = rawViews.mapNotNull { view ->
                    users.find { it.uid == view.visitorUid }?.let { user ->
                        ProfileViewItem(user, view.timestamp)
                    }
                }
                
                _profileViews.value = items
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }




    // 🏆 LEADERBOARD
    private val _leaderboardUsers = MutableStateFlow<List<com.eventos.banana.domain.model.UserProfile>>(emptyList())
    val leaderboardUsers: StateFlow<List<com.eventos.banana.domain.model.UserProfile>> = _leaderboardUsers

    fun loadLeaderboard() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val result = userRepository.getTopUsers(50)
                if (result.isSuccess) {
                     val pair = result.getOrNull()
                     _leaderboardUsers.value = pair?.first ?: emptyList()
                     _uiState.value = ProfileUiState.Success
                } else {
                     _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(result.exceptionOrNull()))
                }
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }


}

