package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.EncounterRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.EncounterMethod
import com.eventos.banana.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NFCEncounterUiState(
    val isLoading: Boolean = false,
    val isNfcActive: Boolean = false,
    val participants: List<UserProfile> = emptyList(),
    val confirmedEncounters: Set<String> = emptySet(), // userIds
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val nfcAvailable: Boolean = true,
    val nfcEnabled: Boolean = true
)

class NFCEncounterViewModel(
    private val eventId: String,
    private val currentUserId: String,
    private val participantIds: List<String>,
    private val encounterRepository: EncounterRepository = EncounterRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NFCEncounterUiState(isLoading = true))
    val uiState: StateFlow<NFCEncounterUiState> = _uiState

    init {
        loadParticipants()
    }

    private fun loadParticipants() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Get participant profiles
                val profiles = participantIds
                    .filter { it != currentUserId } // Exclude self
                    .mapNotNull { userId ->
                        userRepository.getUserProfile(userId)
                    }

                // Get already confirmed encounters
                val metUsers = encounterRepository.getEncountersForUser(eventId, currentUserId)
                    .getOrNull() ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    participants = profiles,
                    confirmedEncounters = metUsers.toSet()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al cargar participantes: ${e.message}"
                )
            }
        }
    }

    /**
     * Registrar encuentro NFC con otro usuario
     */
    fun recordNFCEncounter(detectedUserId: String) {
        viewModelScope.launch {
            if (detectedUserId == currentUserId) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No puedes confirmar encuentro contigo mismo"
                )
                return@launch
            }

            if (detectedUserId in _uiState.value.confirmedEncounters) {
                _uiState.value = _uiState.value.copy(
                    successMessage = "Ya confirmaste encuentro con este usuario"
                )
                return@launch
            }

            val result = encounterRepository.recordEncounter(
                eventId = eventId,
                userId1 = currentUserId,
                userId2 = detectedUserId,
                method = EncounterMethod.NFC_TAP
            )

            if (result.isSuccess) {
                val newConfirmed = _uiState.value.confirmedEncounters + detectedUserId
                _uiState.value = _uiState.value.copy(
                    confirmedEncounters = newConfirmed,
                    successMessage = "✅ Encuentro confirmado"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al registrar encuentro: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    /**
     * 🐛 DEBUG ONLY: Simular encuentro NFC sin hardware
     */
    fun simulateNFCEncounter(userId: String) {
        recordNFCEncounter(userId)
    }

    fun setNfcActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(isNfcActive = active)
    }

    fun setNfcStatus(available: Boolean, enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            nfcAvailable = available,
            nfcEnabled = enabled
        )
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }
}
