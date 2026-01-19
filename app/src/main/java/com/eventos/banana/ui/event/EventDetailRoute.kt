package com.eventos.banana.ui.event

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.eventos.banana.domain.model.EventDetailUiState

@Composable
fun EventDetailRoute(
    uiState: EventDetailUiState,
    currentUserId: String,
    isEmailVerified: Boolean,
    onJoinClick: () -> Unit,
    onApproveClick: (String) -> Unit,
    onRejectClick: (String) -> Unit,
    onCancelEvent: (String) -> Unit,
    onCloseEvent: () -> Unit,
    onRemoveParticipant: (String) -> Unit,
    onDeleteEvent: () -> Unit,
    onRateUser: (String) -> Unit,
    onUserClick: (String) -> Unit
) {
    when (uiState) {

        is EventDetailUiState.Loading -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is EventDetailUiState.Error -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(uiState.message)
            }
        }

        is EventDetailUiState.Success -> {
            EventDetailScreen(
                event = uiState.event,
                currentUserId = currentUserId,
                isEmailVerified = isEmailVerified,
                isJoining = uiState.isJoining,
                onJoinClick = onJoinClick,
                onApproveClick = onApproveClick,
                onRejectClick = onRejectClick,
                onCancelEvent = onCancelEvent,
                onCloseEvent = onCloseEvent,
                onRemoveParticipant = onRemoveParticipant,
                onDeleteEvent = onDeleteEvent,
                onRateUser = onRateUser,
                onUserClick = onUserClick,
                eventState = uiState // Pass full state
            )
        }
    }
}
