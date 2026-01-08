package com.eventos.banana.ui.event

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.Event

@Composable
fun EventDetailScreen(
    event: Event,
    currentUserId: String,
    isJoining: Boolean,
    onJoinClick: () -> Unit,
    onApproveClick: (String) -> Unit,
    onRejectClick: (String) -> Unit
) {
    val isCreator = event.creatorId == currentUserId
    val isApproved = event.approvedParticipants.contains(currentUserId)
    val isPending = event.pendingRequests.contains(currentUserId)
    val isRejected = event.rejectedParticipants.contains(currentUserId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(event.title, style = MaterialTheme.typography.headlineMedium)
        Text("${event.region} • ${event.commune}")

        Divider()

        Text(event.description)

        Divider()

        Text(
            "Cupos: ${event.approvedParticipants.size} / ${event.maxParticipants}"
        )

        // ---------- USUARIO ----------
        if (!isCreator) {
            when {
                isApproved -> DisabledButton("Ya estás aceptado")
                isPending -> DisabledButton("Solicitud enviada")
                isRejected -> DisabledButton("Solicitud rechazada")
                else -> {
                    Button(
                        onClick = onJoinClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isJoining
                    ) {
                        Text("Solicitar acceso")
                    }
                }
            }
        }

        // ---------- CREADOR ----------
        if (isCreator && event.pendingRequests.isNotEmpty()) {

            Divider()
            Text("Solicitudes pendientes")

            event.pendingRequests.forEach { userId ->
                Card {
                    Column(Modifier.padding(12.dp)) {

                        Text(userId)

                        Spacer(Modifier.height(8.dp))

                        Row {
                            OutlinedButton(
                                onClick = { onRejectClick(userId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Rechazar")
                            }

                            Spacer(Modifier.width(8.dp))

                            Button(
                                onClick = { onApproveClick(userId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Aceptar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisabledButton(text: String) {
    Button(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}
