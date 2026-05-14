package com.eventos.banana.ui.event.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eventos.banana.R
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventStatus

@Composable
fun EventDetailActionButtons(
    event: Event,
    isCreator: Boolean,
    isApproved: Boolean,
    isPending: Boolean,
    isRejected: Boolean,
    isJoining: Boolean,
    onJoinClick: () -> Unit,
    onCloseEvent: () -> Unit,
    onCancelEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        // ACCIONES USUARIO (NO CREADOR)
        if (!isCreator) {
            when {
                event.status == EventStatus.CANCELLED ->
                    DisabledButton(stringResource(com.eventos.banana.R.string.event_detail_event_cancelled))

                event.status == EventStatus.CLOSED ->
                    DisabledButton(stringResource(com.eventos.banana.R.string.event_detail_event_closed))

                isApproved ->
                    DisabledButton(stringResource(com.eventos.banana.R.string.event_detail_already_accepted))

                isPending ->
                    DisabledButton(stringResource(com.eventos.banana.R.string.event_detail_request_sent))

                isRejected ->
                    DisabledButton(stringResource(com.eventos.banana.R.string.event_detail_request_rejected))

                else -> {
                    Button(
                        onClick = onJoinClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isJoining
                    ) {
                        Text(if (event.isPublic) stringResource(com.eventos.banana.R.string.event_detail_join_public) else stringResource(com.eventos.banana.R.string.event_detail_join_private))
                    }
                }
            }
        }

        // ACCIONES CREADOR
        if (isCreator) {
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onCloseEvent,
                enabled = event.status == EventStatus.OPEN,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(com.eventos.banana.R.string.event_detail_close_event))
            }

            val cancelReason = stringResource(com.eventos.banana.R.string.event_detail_cancelled_reason)
            OutlinedButton(
                onClick = { onCancelEvent(cancelReason) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(com.eventos.banana.R.string.event_detail_cancel_event))
            }
        }
    }
}

// Botón Helper que ya existía (replicado o migrado)
@Composable
private fun DisabledButton(text: String, modifier: Modifier = Modifier) {
    Button(
        onClick = { },
        enabled = false,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text)
    }
}
