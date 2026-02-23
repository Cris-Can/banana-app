package com.eventos.banana.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.NotificationRepository
import com.eventos.banana.domain.model.AppNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: NotificationRepository
) : ViewModel() {

    private val _notifications =
        MutableStateFlow<List<AppNotification>>(emptyList())

    val notifications: StateFlow<List<AppNotification>> = _notifications

    fun start(userId: String) {
        viewModelScope.launch {
            repository.observeNotifications(userId)
                .catch { e ->
                    android.util.Log.e("NotificationViewModel", "Error: ${e.message}")
                }
                .collect { list ->
                    // Filter out chat messages from the main notification bell
                    _notifications.value = list.filter { 
                        it.type != com.eventos.banana.domain.model.NotificationType.NEW_MESSAGE 
                    }
                }
        }
    }

    // 🔴 A9.5
    fun markAllAsRead(userId: String) {
        viewModelScope.launch {
            repository.markAllAsRead(userId)
        }
    }


}
