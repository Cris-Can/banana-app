package com.eventos.banana.ui.event

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import com.eventos.banana.domain.model.EventDetailUiState

@OptIn(ExperimentalSharedTransitionApi::class)
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
    onUserClick: (String) -> Unit,
    onRateParticipants: (com.eventos.banana.domain.model.Event) -> Unit,
    onBoostClick: () -> Unit,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    hasAttended: Boolean,
    checkInState: com.eventos.banana.viewmodel.CheckInState,
    onCheckInClick: () -> Unit,
    onResetCheckInState: () -> Unit,
    actionState: com.eventos.banana.viewmodel.ActionState = com.eventos.banana.viewmodel.ActionState.Idle,
    onResetActionState: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
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
            
            // ⏰ SCHEDULE REMINDER (A39)
            val event = uiState.event
            val isApproved = event.approvedParticipants.contains(currentUserId) || event.creatorId == currentUserId
            
            if (isApproved && event.startAt > System.currentTimeMillis()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.runtime.LaunchedEffect(event.id) {
                    val timeDiff = event.startAt - System.currentTimeMillis()
                    val oneHour = 60 * 60 * 1000L
                    
                    // Notify 1 hour before, or immediately if less than 1 hour left (but still future)
                    // If timeDiff > 1 hour, delay = timeDiff - 1 hour
                    // If timeDiff < 1 hour, delay = 0 (immediate) - actually better to notify "Starts soon"
                    
                    val delay = if (timeDiff > oneHour) timeDiff - oneHour else 0L
                    
                    val workManager = androidx.work.WorkManager.getInstance(context)
                    
                    val data = androidx.work.workDataOf(
                        "eventId" to event.id,
                        "eventTitle" to event.title
                    )
                    
                    val request = androidx.work.OneTimeWorkRequestBuilder<com.eventos.banana.workers.EventReminderWorker>()
                        .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .setInputData(data)
                        .addTag("reminder_${event.id}")
                        .build()
                        
                    workManager.enqueueUniqueWork(
                        "reminder_${event.id}",
                        androidx.work.ExistingWorkPolicy.KEEP, // Use KEEP to avoid rescheduling if already scheduled
                        request
                    )
                    
                    android.util.Log.d("EventDetailRoute", "Scheduled reminder for ${event.title} in ${delay/1000}s")
                }
            }

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
                onRateParticipants = onRateParticipants,
                onBoostClick = onBoostClick,
                eventState = uiState, // Pass full state
                isSaved = isSaved,
                onToggleSave = onToggleSave,
                hasAttended = hasAttended,
                checkInState = checkInState,
                onCheckInClick = onCheckInClick,
                onResetCheckInState = onResetCheckInState,
                actionState = actionState,
                onResetActionState = onResetActionState,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    }
}
