package com.eventos.banana.ui.event

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventStatus

@Composable
fun EventDetailScreen(
    event: Event,
    currentUserId: String,
    isJoining: Boolean,
    onJoinClick: () -> Unit,
    onApproveClick: (String) -> Unit,
    onRejectClick: (String) -> Unit,
    onCancelEvent: (String) -> Unit,
    onCloseEvent: () -> Unit,
    onRemoveParticipant: (String) -> Unit
) {
    val isCreator = event.creatorId == currentUserId
    val isApproved = event.approvedParticipants.contains(currentUserId)
    val isPending = event.pendingRequests.any { it.userId == currentUserId }
    val isRejected = event.rejectedParticipants.contains(currentUserId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ---------- HEADER ----------
        Text(event.title, style = MaterialTheme.typography.headlineMedium)
        Text("${event.region} • ${event.commune}")

        // ---------- ESTADO ----------
        when (event.status) {
            EventStatus.CANCELLED -> {
                Text(
                    "❌ Evento cancelado",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                return
            }

            EventStatus.CLOSED -> {
                Text("🔒 Evento cerrado", fontWeight = FontWeight.Bold)
            }

            EventStatus.OPEN -> Unit

            else -> Unit
        }

        Divider()
        Text(event.description)
        Divider()

        Text("Cupos: ${event.approvedParticipants.size} / ${event.maxParticipants}")

        // =====================================================
        // ACCIONES USUARIO (NO CREADOR)
        // =====================================================
        if (!isCreator) {
            when {
                event.status == EventStatus.CANCELLED ->
                    DisabledButton("Evento cancelado")

                event.status == EventStatus.CLOSED ->
                    DisabledButton("Evento cerrado")

                isApproved ->
                    DisabledButton("Ya estás aceptado")

                isPending ->
                    DisabledButton("Solicitud enviada")

                isRejected ->
                    DisabledButton("Solicitud rechazada")

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


        // =====================================================
        // ACCIONES CREADOR
        // =====================================================
        if (isCreator) {

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onCloseEvent,
                enabled = event.status == EventStatus.OPEN,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar evento")
            }

            OutlinedButton(
                onClick = { onCancelEvent("Cancelado por el organizador") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar evento")
            }
        }

        // =====================================================
        // PARTICIPANTES APROBADOS
        // =====================================================
        if (isCreator && event.approvedParticipants.isNotEmpty()) {
            Divider()
            Text("Participantes", style = MaterialTheme.typography.titleMedium)

            event.approvedParticipants.forEach { userId ->
                val isCreatorParticipant = userId == event.creatorId

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(userId)

                    if (!isCreatorParticipant) {
                        TextButton(
                            onClick = { onRemoveParticipant(userId) }
                        ) {
                            Text("Eliminar")
                        }
                    }
                }
            }
        }

        // =====================================================
        // SOLICITUDES PENDIENTES
        // =====================================================
        if (isCreator && event.pendingRequests.isNotEmpty()) {
            Divider()
            Text("Solicitudes pendientes", style = MaterialTheme.typography.titleMedium)

            event.pendingRequests.forEach { request ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {

                        Text("Usuario: ${request.userId}")
                        Spacer(Modifier.height(8.dp))

                        request.answers.forEach { (_, answer) ->
                            Text(answer)
                            Spacer(Modifier.height(4.dp))
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onRejectClick(request.userId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Rechazar")
                            }

                            Button(
                                onClick = { onApproveClick(request.userId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Aceptar")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
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
