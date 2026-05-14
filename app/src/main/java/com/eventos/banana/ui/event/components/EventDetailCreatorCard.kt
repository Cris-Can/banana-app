package com.eventos.banana.ui.event.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventDetailUiState

@Composable
fun EventDetailCreatorCard(
    event: Event,
    eventState: EventDetailUiState,
    currentUserId: String,
    isCreator: Boolean,
    onUserClick: (String) -> Unit,
    onBoostClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Creator Card
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onUserClick(event.creatorId) },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Organizado por:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))

                val creatorProfile = (eventState as? EventDetailUiState.Success)
                    ?.userProfiles?.get(event.creatorId)
                val creatorNickname = creatorProfile?.nickname ?: "Cargando..."
                val isCreatorGold = creatorProfile?.isGold == true

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        creatorNickname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isCreatorGold) {
                        Spacer(Modifier.width(4.dp))
                        Text("👑", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // 🚀 CREATOR TOOLS: BOOST (Round 42)
        if (isCreator) {
            var showBoostDialog by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!event.isBoosted) {
                            showBoostDialog = true
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (event.isBoosted)
                        Color(0xFFFFD700).copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (event.isBoosted) Color(0xFFFFD700) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (event.isBoosted) "🔥 Evento Destacado" else "🚀 Destacar Evento",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (event.isBoosted) Color(0xFFB8860B) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (event.isBoosted) "Tu evento tiene prioridad en el feed." else "Consigue más asistentes y visibilidad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!event.isBoosted) {
                        Button(
                            onClick = { showBoostDialog = true },
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color.Black
                            )
                        ) {
                            Text(stringResource(com.eventos.banana.R.string.event_detail_boost))
                        }
                    }
                }
            }

            if (showBoostDialog) {
                AlertDialog(
                    onDismissRequest = { showBoostDialog = false },
                    title = { Text(stringResource(com.eventos.banana.R.string.event_detail_boost_title)) },
                    text = {
                        val currentUserProfile = (eventState as? EventDetailUiState.Success)
                            ?.userProfiles?.get(currentUserId)
                        val isFounder = currentUserProfile?.isFounder == true

                        Column {
                            Text(stringResource(com.eventos.banana.R.string.event_detail_boost_benefits))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(com.eventos.banana.R.string.event_detail_boost_bullet1))
                            Text(stringResource(com.eventos.banana.R.string.event_detail_boost_bullet2))
                            Text(stringResource(com.eventos.banana.R.string.event_detail_boost_bullet3))
                            Spacer(Modifier.height(16.dp))

                            if (isFounder) {
                                Text(
                                    "Precio especial para Founders: $1.000 CLP",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            Text(stringResource(com.eventos.banana.R.string.event_detail_boost_confirm), fontWeight = FontWeight.Bold)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showBoostDialog = false
                                onBoostClick()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color.Black
                            )
                        ) {
                            Text(stringResource(com.eventos.banana.R.string.event_detail_boost_get))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBoostDialog = false }) {
                            Text(stringResource(com.eventos.banana.R.string.common_cancel))
                        }
                    }
                )
            }
        }
    }
}
