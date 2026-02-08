package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        commune: String,
        lat: Double,
        lng: Double
    ) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val geohash = com.eventos.banana.util.GeohashUtils.encode(lat, lng, 9)
                userRepository.updateLocation(uid, region, commune, lat, lng, geohash)
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
            try {
                userRepository.updateAppTheme(uid, theme)
                // We rely on SessionViewModel listener to update the main UI, 
                // but we can locally handle errors.
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
            }
        }
    }

    fun uploadPhoto(uid: String, imageBytes: ByteArray?, isProfilePicture: Boolean = false, isCoverPhoto: Boolean = false) {
        if (imageBytes == null) return
        
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                if (!isProfilePicture && !isCoverPhoto) {
                    val profile = userRepository.getUserProfile(uid)
                    if (profile != null && profile.photos.size >= 6) {
                        _uiState.value = ProfileUiState.Error("Límite de 6 fotos alcanzado (máx. 6)")
                        return@launch
                    }
                }
                userRepository.uploadProfilePhoto(uid, imageBytes, isProfilePicture, isCoverPhoto)
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

    private val _debugStatus = MutableStateFlow<String>("Esperando carga...")
    val debugStatus: StateFlow<String> = _debugStatus

    fun loadUserEvents(uid: String, savedEventIds: List<String>) {
        viewModelScope.launch {
            // Obtener email para debug
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val authEmail = currentUser?.email ?: "No Auth Email"
            var firestoreProfile = com.eventos.banana.data.repository.UserRepository().getUserProfile(uid)
            val firestoreEmail = firestoreProfile?.email ?: "No FS Profile"
            
            // 🔧 AUTO-REPAIR MECHANISM
            if (firestoreProfile == null && currentUser != null) {
                 _debugStatus.value = "⚠️ Perfil corrupto. Intentando autoreparación..."
                 
                 // 📊 ANALYTICS: Log Attempt (using Crashlytics for stability monitoring)
                 com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("Auto-Repair: Attempting to repair missing profile for UID: $uid")
                 
                 try {
                     val repairedProfile = com.eventos.banana.domain.model.UserProfile(
                        uid = uid,
                        email = authEmail,
                        nickname = authEmail.substringBefore('@'),
                        createdAt = System.currentTimeMillis() // Reset creation date
                     )
                     com.eventos.banana.data.repository.UserRepository().createUserProfile(repairedProfile)
                     _debugStatus.value = "✅ Perfil reparado. Recargando..."
                     
                     // 📊 ANALYTICS: Log Success
                     com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("Auto-Repair: SUCCESS for UID: $uid")
                     com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCustomKey("repaired_profile", true)

                     // Reload local variable
                     firestoreProfile = repairedProfile
                 } catch (e: Exception) {
                     _debugStatus.value = "❌ Error reparando: ${e.message}"
                     
                     // 📊 ANALYTICS: Log Failure
                     com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(
                        Exception("Auto-Repair Failed for $uid: ${e.message}")
                     )
                 }
            } else {
                 _debugStatus.value = "Cargando... \nUID: $uid\nAuth: $authEmail\nFS: $firestoreEmail"
            }

            try {
                // OPTIMIZATION: Force SERVER to bypass any stuck local cache
                val source = com.google.firebase.firestore.Source.SERVER
                
                // PARALLEL FETCH
                val createdEventsJob = async {
                    try {
                        val res = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("events")
                            .whereEqualTo("creatorId", uid)
                            .get(source).await()
                        res.toObjects(com.eventos.banana.domain.model.Event::class.java)
                    } catch (e: Exception) { 
                        _debugStatus.value = "Error Created: ${e.message}"
                        emptyList<com.eventos.banana.domain.model.Event>() 
                    }
                }
                
                val participatedEventsJob = async {
                    try {
                        val res = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("events")
                            .whereArrayContains("approvedParticipants", uid)
                            .get(source).await()
                        res.toObjects(com.eventos.banana.domain.model.Event::class.java)
                    } catch (e: Exception) { 
                        _debugStatus.value = "Error Participated: ${e.message}"
                        emptyList<com.eventos.banana.domain.model.Event>() 
                    }
                }

                val savedEventsJob = async {
                     if (savedEventIds.isNotEmpty()) {
                        try {
                            if (savedEventIds.size <= 30) {
                                val chunks = savedEventIds.chunked(10)
                                val results = mutableListOf<com.eventos.banana.domain.model.Event>()
                                chunks.forEach { chunk ->
                                     val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("events")
                                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                        .get(source).await()
                                     results.addAll(snapshot.toObjects(com.eventos.banana.domain.model.Event::class.java))
                                }
                                results
                            } else {
                                emptyList<com.eventos.banana.domain.model.Event>() 
                            }
                        } catch (e: Exception) { 
                            emptyList<com.eventos.banana.domain.model.Event>() 
                        }
                     } else {
                         emptyList<com.eventos.banana.domain.model.Event>()
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

    fun recalculateStats(uid: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                userRepository.recalculateUserStats(uid)
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(getFriendlyErrorMessage(e))
                android.util.Log.e("ProfileViewModel", "Recalculate stats failed", e)
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

    // =========================================================
    // MIGRATION TOOL (A29)
    // =========================================================
    private val _migrationStatus = MutableStateFlow<String?>(null)
    val migrationStatus: StateFlow<String?> = _migrationStatus.asStateFlow()

    fun runMigration() {
        viewModelScope.launch {
            _migrationStatus.value = "⏳ Migrando eventos..."
            val result = com.eventos.banana.data.repository.EventRepository().migrateEventsToGeohash()
            
            result.onSuccess { count ->
                _migrationStatus.value = "✅ Éxito: $count eventos actualizados con Geohash."
            }.onFailure { e ->
                _migrationStatus.value = "❌ Error: ${e.message}"
            }
            
            // Clear status after 5 seconds
            kotlinx.coroutines.delay(5000)
            _migrationStatus.value = null
        }
    }
}

