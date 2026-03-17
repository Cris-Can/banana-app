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
    
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    fun start(userId: String) {
        viewModelScope.launch {
            repository.observeNotifications(userId)
                .catch { e ->
                    android.util.Log.e("NotificationViewModel", "Error: ${e.message}")
                }
                .collect { list ->
                    // Mostramos todas las notificaciones relevantes
                    _notifications.value = list
                    _unreadCount.value = list.count { !it.read }
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
