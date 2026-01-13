package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.NotificationRepository
import com.eventos.banana.domain.model.AppNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository = NotificationRepository()
) : ViewModel() {

    private val _notifications =
        MutableStateFlow<List<AppNotification>>(emptyList())

    val notifications: StateFlow<List<AppNotification>> = _notifications

    fun start(userId: String) {
        viewModelScope.launch {
            repository.observeNotifications(userId).collect { list ->
                _notifications.value = list
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
