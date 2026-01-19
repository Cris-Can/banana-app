package com.eventos.banana.ui.event

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventStatus

@Composable
fun EventDetailScreen(
    event: Event,
    currentUserId: String,
    isEmailVerified: Boolean = false,
    isJoining: Boolean,
    onJoinClick: () -> Unit,
    onApproveClick: (String) -> Unit,
    onRejectClick: (String) -> Unit,
    onCancelEvent: (String) -> Unit,
    onCloseEvent: () -> Unit,
    onRemoveParticipant: (String) -> Unit,
    onDeleteEvent: () -> Unit,
    onRateUser: (String) -> Unit,
    onUserClick: (String) -> Unit,
    eventState: com.eventos.banana.domain.model.EventDetailUiState // Pass full state to access nicknames
) {
    val context = LocalContext.current
    val isCreator = event.creatorId == currentUserId
    val isApproved = event.approvedParticipants.contains(currentUserId)
    val isPending = event.pendingRequests.any { it.userId == currentUserId }
    val isRejected = event.rejectedParticipants.contains(currentUserId)
    val canSeeFeed = isCreator || isApproved

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Detalles", "Muro")

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ---------- HEADER ----------
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(event.title, style = MaterialTheme.typography.headlineMedium)
            Text("${event.region} • ${event.commune}")
        }

        // ---------- TABS ----------
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        // ---------- CONTENT BASED ON SELECTED TAB ----------
        if (selectedTab == 0) {
            // DETALLES TAB
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ========== FOTO DEL EVENTO ==========
                if (!event.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = event.imageUrl,
                        contentDescription = "Foto del evento",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // Contenido con padding
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ========== CREADOR ==========
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "👑 Organizador:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = { onUserClick(event.creatorId) }
                            ) {
                                val creatorNickname = (eventState as? com.eventos.banana.domain.model.EventDetailUiState.Success)
                                    ?.userNicknames?.get(event.creatorId) ?: event.creatorId

                                Text(
                                    creatorNickname,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // ========== PARTICIPANTES ==========
                    if (event.approvedParticipants.isNotEmpty()) {
                        var showParticipantsDialog by remember { mutableStateOf(false) }
                        
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        if (isCreator || isApproved) {
                                            showParticipantsDialog = true 
                                        } else {
                                            android.widget.Toast.makeText(context, "Debes unirte para ver a los participantes", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "👥 Participantes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${event.approvedParticipants.size}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        // Dialog estilo Instagram
                        if (showParticipantsDialog) {
                            AlertDialog(
                                onDismissRequest = { showParticipantsDialog = false },
                                title = {
                                    Text("Participantes (${event.approvedParticipants.size})")
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    ) {
                                        event.approvedParticipants.forEach { userId ->
                                            // Nickname lookup inside Dialog
                                            val nickname = (eventState as? com.eventos.banana.domain.model.EventDetailUiState.Success)
                                                ?.userNicknames?.get(userId) ?: userId

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { 
                                                        showParticipantsDialog = false
                                                        onUserClick(userId) 
                                                    }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    nickname,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }
                                            if (userId != event.approvedParticipants.last()) {
                                                HorizontalDivider()
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showParticipantsDialog = false }) {
                                        Text("Cerrar")
                                    }
                                }
                            )
                        }
                    }

                    // ========== UBICACIÓN EN MAPA ==========
                    if (event.exactLatitude != null && event.exactLongitude != null) {
                        val canSeeMap = isCreator || isApproved
                        
                        if (canSeeMap) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "🗺️ Ubicación Exacta",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!event.exactAddress.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            event.exactAddress,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val uri = android.net.Uri.parse("geo:0,0?q=${event.exactLatitude},${event.exactLongitude}(${android.net.Uri.encode(event.title)})")
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                            intent.setPackage("com.google.android.apps.maps")
                                            try {
                                                androidx.core.content.ContextCompat.startActivity(context, intent, null)
                                            } catch (e: Exception) {
                                                // Fallback to browser or generic map handler if Google Maps app is not installed
                                                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                androidx.core.content.ContextCompat.startActivity(context, fallbackIntent, null)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Ver en Mapa")
                                    }
                                }
                            }
                        }
                    }

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

                // ACCIONES USUARIO (NO CREADOR)
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

                // ACCIONES CREADOR
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

                // SOLICITUDES PENDIENTES
                if (isCreator && event.pendingRequests.isNotEmpty()) {
                    Divider()
                    Text("Solicitudes pendientes", style = MaterialTheme.typography.titleMedium)

                    event.pendingRequests.forEach { request ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {

                                val requesterNickname = (eventState as? com.eventos.banana.domain.model.EventDetailUiState.Success)
                                    ?.userNicknames?.get(request.userId) ?: request.userId

                                Text("Usuario: $requesterNickname")
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
                }  // End inner Column (with padding)
            }  // End outer Column (scrollable)
        } else {
            // MURO TAB
            if (canSeeFeed) {
                EventFeedSection(
                    eventId = event.id,
                    currentUserId = currentUserId,
                    onUserClick = onUserClick
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Debes unirte al evento para ver el muro",
                        style = MaterialTheme.typography.bodyLarge
                    )
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
