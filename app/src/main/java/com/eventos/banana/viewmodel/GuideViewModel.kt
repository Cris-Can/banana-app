package com.eventos.banana.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class GuideStep {
    HOME_WELCOME,
    HOME_FILTERS,
    NAV_TO_CREATE,
    CREATE_EXPLAIN,
    NAV_TO_PROFILE,
    PROFILE_EXPLAIN,
    FINISH
}

class GuideViewModel(
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _currentStep = MutableStateFlow<GuideStep?>(null)
    val currentStep: StateFlow<GuideStep?> = _currentStep

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible

    // Events to trigger navigation in AppNavigation
    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent: SharedFlow<String> = _navigationEvent

    init {
        // Check if guide was already completed
        val isGuideCompleted = sharedPreferences.getBoolean("guide_completed_v1", false)
        if (!isGuideCompleted) {
            // Start automatically after a short delay or immediately
            // For now, we wait for explicit start call from UI or auto-start logic
        }
    }

    fun startGuide() {
        if (sharedPreferences.getBoolean("guide_completed_v1", false)) return
        _currentStep.value = GuideStep.HOME_WELCOME
        _isVisible.value = true
    }

    fun nextStep() {
        val current = _currentStep.value ?: return
        
        viewModelScope.launch {
            when (current) {
                GuideStep.HOME_WELCOME -> _currentStep.value = GuideStep.HOME_FILTERS
                GuideStep.HOME_FILTERS -> {
                    _currentStep.value = GuideStep.NAV_TO_CREATE
                }
                GuideStep.NAV_TO_CREATE -> {
                     _navigationEvent.emit("create_event")
                    _currentStep.value = GuideStep.CREATE_EXPLAIN
                }
                GuideStep.CREATE_EXPLAIN -> {
                    _currentStep.value = GuideStep.NAV_TO_PROFILE
                }
                GuideStep.NAV_TO_PROFILE -> {
                    _navigationEvent.emit("profile")
                    _currentStep.value = GuideStep.PROFILE_EXPLAIN
                }
                GuideStep.PROFILE_EXPLAIN -> _currentStep.value = GuideStep.FINISH
                GuideStep.FINISH -> completeGuide()
            }
        }
    }

    fun skipGuide() {
        completeGuide()
    }

    private fun completeGuide() {
        _isVisible.value = false
        _currentStep.value = null
        sharedPreferences.edit().putBoolean("guide_completed_v1", true).apply()
    }
}

class GuideViewModelFactory(private val sharedPreferences: SharedPreferences) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GuideViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GuideViewModel(sharedPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
