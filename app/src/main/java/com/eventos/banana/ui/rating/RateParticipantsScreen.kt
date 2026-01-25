package com.eventos.banana.ui.rating

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.EventType
import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.viewmodel.RatingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateParticipantsScreen(
    eventId: String,
    eventType: EventType,
    currentUserId: String,
    participantIds: List<String>,
    onBackClick: () -> Unit
) {
    val viewModel = remember {
        RatingViewModel(
            eventId = eventId,
            eventType = eventType,
            currentUserId = currentUserId,
            participantIds = participantIds
        )
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show messages
    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("⭐ Calificar Participantes") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Puntúa a los participantes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Califica su comportamiento y compromiso (1-5 estrellas)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            
                            val totalUsers = uiState.usersToRate.size
                            val rated = uiState.alreadyRated.size
                            val pending = totalUsers - rated
                            
                            Text(
                                "Progreso: $rated / $totalUsers puntuados",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            LinearProgressIndicator(
                                progress = if (totalUsers > 0) rated.toFloat() / totalUsers else 0f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }
                    }
                }

                // Participants list
                items(uiState.usersToRate) { user ->
                    ParticipantRatingCard(
                        user = user,
                        alreadyRated = user.uid in uiState.alreadyRated,
                        onSubmitRating = { score, comment ->
                            viewModel.submitRating(user.uid, score, comment)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantRatingCard(
    user: UserProfile,
    alreadyRated: Boolean,
    onSubmitRating: (Int, String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedScore by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alreadyRated) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            // User info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        user.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (user.ratingCount > 0) {
                        Text(
                            "${user.getRatingBadge()} ${String.format("%.1f", user.averageRating)} (${user.ratingCount})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (alreadyRated) {
                    Text(
                        "✅ Puntuado",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Cancelar" else "Puntuar")
                    }
                }
            }

            // Rating form (collapsed by default)
            if (expanded && !alreadyRated) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                
                // Star rating
                Text(
                    "Selecciona puntuación:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 1..5) {
                        IconButton(
                            onClick = { selectedScore = i }
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "$i estrella${if (i > 1) "s" else ""}",
                                tint = if (i <= selectedScore) Color(0xFFFFD700) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
                
                if (selectedScore > 0) {
                    Text(
                        when (selectedScore) {
                            1 -> "⚠️ Muy malo"
                            2 -> "😕 Malo"
                            3 -> "😐 Regular"
                            4 -> "😊 Bueno"
                            5 -> "🌟 Excelente"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Comment (optional)
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comentario (Opcional)") },
                    placeholder = { Text("Explica tu puntuación...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    supportingText = { 
                        Text("Solo visible para usuarios Premium")
                    }
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Submit button
                Button(
                    onClick = {
                        if (selectedScore > 0) {
                            onSubmitRating(selectedScore, comment.ifBlank { null })
                            expanded = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedScore > 0
                ) {
                    Text("Enviar Puntuación")
                }
            }
        }
    }
}
