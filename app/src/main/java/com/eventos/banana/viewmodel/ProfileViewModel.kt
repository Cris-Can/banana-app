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
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState

    fun updateNickname(uid: String, nickname: String) {
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
                _uiState.value = ProfileUiState.Error(
                    e.message ?: "Error al actualizar perfil"
                )
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
                _uiState.value = ProfileUiState.Error(
                    "No se pudo guardar la ubicación"
                )
            }
        }
    }

    // 🔔 NUEVO: actualizar preferencia de notificación
    fun updateNotifyEventsByCommune(uid: String, enabled: Boolean) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                userRepository.updateNotifyEventsByCommune(uid, enabled)
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(
                    "No se pudo actualizar la preferencia"
                )
            }
        }
    }
}
