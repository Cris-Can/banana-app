package com.eventos.banana.ui.event.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventDetailUiState

@Composable
fun EventDetailParticipantsCard(
    event: Event,
    eventState: EventDetailUiState,
    isCreator: Boolean,
    isApproved: Boolean,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (event.approvedParticipants.isNotEmpty()) {
        var showParticipantsDialog by remember { mutableStateOf(false) }
        
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable { 
                    if (isCreator || isApproved || event.isPublic) {
                        showParticipantsDialog = true 
                    } else {
                        Toast.makeText(context, "Debes unirte para ver a los participantes", Toast.LENGTH_SHORT).show()
                    }
                },
            shape = RoundedCornerShape(12.dp)
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
                    Text(stringResource(com.eventos.banana.R.string.event_detail_participants, event.approvedParticipants.size))
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        event.approvedParticipants.forEach { userId ->
                            // Nickname lookup inside Dialog
                            val participantProfile = (eventState as? EventDetailUiState.Success)
                                ?.userProfiles?.get(userId)
                            val nickname = participantProfile?.nickname ?: "Usuario"
                            val isGold = participantProfile?.isGold == true

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
                                if (!participantProfile?.profilePictureUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = participantProfile?.profilePictureUrl,
                                        contentDescription = nickname,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            nickname.take(1).uppercase(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    nickname,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (isGold) {
                                    Spacer(Modifier.width(4.dp))
                                    Text("👑", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            if (userId != event.approvedParticipants.last()) {
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showParticipantsDialog = false }) {
                        Text(stringResource(com.eventos.banana.R.string.common_close))
                    }
                }
            )
        }
    }
}
