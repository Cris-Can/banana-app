package com.eventos.banana.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AdminToolsUiState(
    val snackbarMessage: String? = null,
    val isGenerating: Boolean = false,
    val generatedCode: String? = null,
    val selectedDurationDays: Int? = 30,
    val isCleaning: Boolean = false,
    val showCleanupConfirm: Boolean = false
)

@HiltViewModel
class AdminToolsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminToolsUiState())
    val state: StateFlow<AdminToolsUiState> = _state.asStateFlow()

    fun generateFounderCode(currentUserId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, snackbarMessage = null) }
            val duration = _state.value.selectedDurationDays
            val result = userRepository.generateFounderCode(currentUserId, duration)
            if (result.isSuccess) {
                val code = result.getOrNull().also {
                    if (it == null) {
                        Timber.w("generateFounderCode returned success with null code")
                    }
                }
                _state.update { it.copy(generatedCode = code, isGenerating = false) }
            } else {
                _state.update {
                    it.copy(
                        snackbarMessage = "❌ Error: ${result.exceptionOrNull()?.message ?: "No se pudo crear el código"}",
                        isGenerating = false
                    )
                }
            }
        }
    }

    fun setSelectedDurationDays(days: Int?) {
        _state.update { it.copy(selectedDurationDays = days) }
    }

    fun clearGeneratedCode() {
        _state.update { it.copy(generatedCode = null) }
    }

    fun showCleanupConfirm(show: Boolean) {
        _state.update { it.copy(showCleanupConfirm = show) }
    }

    fun cleanupDatabase() {
        viewModelScope.launch {
            _state.update { it.copy(isCleaning = true, showCleanupConfirm = false) }
            val result = userRepository.cleanupUsersDatabase()
            if (result.isSuccess) {
                val message = result.getOrNull().also {
                    if (it == null) {
                        Timber.w("cleanupUsersDatabase returned success with null message")
                    }
                }
                _state.update { it.copy(snackbarMessage = "✅ $message", isCleaning = false) }
            } else {
                _state.update { it.copy(snackbarMessage = "❌ Error: ${result.exceptionOrNull()?.message}", isCleaning = false) }
            }
        }
    }

    fun dismissSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }
}
