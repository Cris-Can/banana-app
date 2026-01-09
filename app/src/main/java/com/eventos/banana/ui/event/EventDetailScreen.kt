package com.eventos.banana.ui.event

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    val isPending = event.pendingRequests.any { it.userId == currentUserId }
    val isRejected = event.rejectedParticipants.contains(currentUserId)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(event.title, style = MaterialTheme.typography.headlineMedium)
        Text("${event.region} • ${event.commune}")

        Divider()
        Text(event.description)
        Divider()

        Text("Cupos: ${event.approvedParticipants.size} / ${event.maxParticipants}")

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
            Text("Solicitudes pendientes", style = MaterialTheme.typography.titleMedium)

            event.pendingRequests.forEach { request ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {

                        Text("Usuario: ${request.userId}")

                        Spacer(Modifier.height(8.dp))

                        // 🔥 AQUÍ YA SE VE EL TEXTO REAL
                        request.answers.forEach { (questionId, answer) ->

                            val questionText = event.joinQuestions
                                .find { it.id == questionId }
                                ?.text ?: "Pregunta eliminada"

                            Text("• $questionText", fontWeight = FontWeight.Bold)
                            Text(answer)

                            Spacer(Modifier.height(6.dp))
                        }

                        Spacer(Modifier.height(12.dp))

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
    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}
