package com.eventos.banana.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventos.banana.viewmodel.FriendStatus
import com.eventos.banana.viewmodel.PublicProfileViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    targetUserId: String,
    onBack: () -> Unit,
    onMessageClick: (String) -> Unit = {},  // userId to start chat with
    isCurrentUserVerified: Boolean = false, // 🔒 Restriction check
    viewModel: PublicProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(targetUserId) {
        viewModel.loadProfile(targetUserId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.profile?.nickname ?: "Perfil", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            } else {
                val profile = uiState.profile
                if (profile != null) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header: Avatar + Stats
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            // Estado para expandir avatar
                            var showExpandedAvatar by remember { mutableStateOf(false) }

                            if (!profile.profilePictureUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = profile.profilePictureUrl,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(Color.Gray, androidx.compose.foundation.shape.CircleShape)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .clickable { showExpandedAvatar = true }, // 🔍 Click para expandir
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.size(80.dp).background(Color.Gray, androidx.compose.foundation.shape.CircleShape))
                            }
                            
                            // 🔍 Dialogo de Avatar Expandido
                            if (showExpandedAvatar && !profile.profilePictureUrl.isNullOrBlank()) {
                                androidx.compose.ui.window.Dialog(
                                    onDismissRequest = { showExpandedAvatar = false }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f) // Cuadrado o ajustar según imagen
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                            .background(Color.Black),
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = profile.profilePictureUrl,
                                            contentDescription = "Avatar Completo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                        // Botón cerrar opcional (el usuario puede tocar fuera)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(profile.nickname, style = MaterialTheme.typography.headlineSmall)
                                // Rating
                                if(profile.ratingCount > 0) {
                                    val avg = profile.ratingSum / profile.ratingCount
                                    Text("⭐ ${String.format("%.1f", avg)} (${profile.ratingCount})", color = Color(0xFFFFC107))
                                }
                            }
                        }

                        // Friend Action Button & Messaging
                        when (uiState.friendStatus) {
                            FriendStatus.NONE -> {
                                Button(onClick = { viewModel.sendFriendRequest(profile.uid) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Agregar a mis amigos")
                                }
                                // Messaging blocked if not friends
                            }
                            FriendStatus.REQUEST_SENT -> {
                                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                    Text("Solicitud enviada")
                                }
                            }
                            FriendStatus.REQUEST_RECEIVED -> {
                                Button(onClick = { viewModel.acceptFriendRequest(profile.uid) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Aceptar solicitud de amistad")
                                }
                            }
                            FriendStatus.FRIEND -> {
                                // Only allow messaging if BOTH are friends AND verified (per strict user request)
                                val isTargetVerified = profile.isVerified
                                
                                if (isCurrentUserVerified && isTargetVerified) {
                                    Button(onClick = { onMessageClick(profile.uid) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("💬 Enviar Mensaje")
                                    }
                                } else {
                                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                        val reason = if (!isCurrentUserVerified) "Verifícate para enviar mensajes" else "Usuario no verificado"
                                        Text("⚠️ $reason")
                                    }
                                }
                            }
                            FriendStatus.SELF -> {
                                OutlinedCard(
                                    colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                        Text("Este es tu perfil público 👤", color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        // Removed standalone Message Button block to integrate above


                        HorizontalDivider()

                        // About Me
                        if (profile.aboutMe.isNotBlank()) {
                            Text("Sobre mí", style = MaterialTheme.typography.titleMedium)
                            Text(profile.aboutMe, style = MaterialTheme.typography.bodyMedium)
                        }

                        // Interests
                        if (profile.interests.isNotEmpty()) {
                            Text("Intereses", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                profile.interests.forEach {
                                    AssistChip(onClick = {}, label = { Text(it) })
                                }
                            }
                        }

                        // Photos
                        if (profile.photos.isNotEmpty()) {
                            Text("Fotos", style = MaterialTheme.typography.titleMedium)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                profile.photos.forEach { photoUrl ->
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(100.dp)
                                            .background(Color.LightGray),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                } else if (uiState.error != null) {
                    Text("Error: ${uiState.error}", modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                }
            }
        }
    }
}
