package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    object Success : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: com.eventos.banana.data.repository.AuthRepository = com.eventos.banana.data.repository.AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState

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

    fun updateLocation(uid: String, region: String, commune: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                userRepository.updateLocation(uid, region, commune)
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }

    fun updateNotifyEventsByCommune(uid: String, enabled: Boolean, region: String?, commune: String?) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                userRepository.updateNotifyEventsByCommune(uid, enabled, region, commune)
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
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
            val currentProfile = (userRepository.getUserProfile(uid) ?: return@launch)
            val currentSubscriptions = currentProfile.subscribedCategories.toMutableList()
            
            if (isEnabled) {
                if (!currentSubscriptions.contains(categoryTopic)) {
                    currentSubscriptions.add(categoryTopic)
                }
            } else {
                currentSubscriptions.remove(categoryTopic)
            }
            
            try {
                userRepository.updateSubscribedCategories(uid, currentSubscriptions)
            } catch (e: Exception) {
                 _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }

    fun updateLocationFromDevice(
        uid: String,
        region: String,
        commune: String
    ) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                userRepository.updateLocation(uid, region, commune)
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
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
                userRepository.updateSocialProfile(uid, aboutMe, interests)
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
            try {
                userRepository.updateAppTheme(uid, theme)
                // We rely on SessionViewModel listener to update the main UI, 
                // but we can locally handle errors.
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }

    fun uploadPhoto(uid: String, imageBytes: ByteArray?, isProfilePicture: Boolean = false) {
        if (imageBytes == null) return
        
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                if (!isProfilePicture) {
                    val profile = userRepository.getUserProfile(uid)
                    if (profile != null && profile.photos.size >= 6) {
                        _uiState.value = ProfileUiState.Error("Límite de 6 fotos alcanzado (máx. 6)")
                        return@launch
                    }
                }
                userRepository.uploadProfilePhoto(uid, imageBytes, isProfilePicture)
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }

    // =====================================================
    // 🤝 A20 — FRIENDS
    // =====================================================
    fun sendFriendRequest(currentUid: String, targetUid: String) {
        viewModelScope.launch {
            try {
                userRepository.sendFriendRequest(currentUid, targetUid)
                // Usamos Success genérico o un estado específico
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }

    fun acceptFriendRequest(currentUid: String, requesterUid: String) {
        viewModelScope.launch {
            try {
                userRepository.acceptFriendRequest(currentUid, requesterUid)
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
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

    fun loadUserEvents(uid: String, savedEventIds: List<String>) {
        viewModelScope.launch {
            try {
                val eventRepo = com.eventos.banana.data.repository.EventRepository()
                val result = eventRepo.getEvents()
                if (result.isSuccess) {
                    val allEvents = result.getOrNull() ?: emptyList()
                    val now = System.currentTimeMillis()
                    val oneWeekMillis = 7 * 24 * 60 * 60 * 1000L

                    // HISTORY: Past events (attended/created) within 1 week retention
                    _historyEvents.value = allEvents.filter { event ->
                        val isAttended = event.creatorId == uid || event.approvedParticipants.contains(uid)
                        val isPast = event.endAt < now || event.status == com.eventos.banana.domain.model.EventStatus.CLOSED || event.status == com.eventos.banana.domain.model.EventStatus.CANCELLED
                        val isRecent = (event.endAt + oneWeekMillis) > now
                        
                        isAttended && isPast && isRecent
                    }

                    // SAVED: Events in user's saved list (No time limit)
                    _savedEvents.value = allEvents.filter { event ->
                        savedEventIds.contains(event.id)
                    }
                }
            } catch (e: Exception) {
                // Fail silently for history
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
}
