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
    onJoinClick: () -> Unit,
    onApproveClick: (String) -> Unit,
    onRejectClick: (String) -> Unit
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
                isJoining = false,
                onJoinClick = onJoinClick,
                onApproveClick = onApproveClick,
                onRejectClick = onRejectClick
            )
        }
    }
}
