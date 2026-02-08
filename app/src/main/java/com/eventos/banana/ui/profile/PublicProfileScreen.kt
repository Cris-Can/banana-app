package com.eventos.banana.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border // ➕
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex // ➕
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.MoreVert
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
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    var showReportDialog by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "Opciones")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("🚩 Denunciar usuario") },
                            onClick = { 
                                showMenu = false 
                                showReportDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🚫 Bloquear usuario") },
                            onClick = { 
                                showMenu = false
                                viewModel.blockUser(targetUserId)
                                onBack() // Exit profile after blocking
                             }
                        )
                    }
                    
                    if (showReportDialog) {
                         // Simple Report Dialog
                         AlertDialog(
                             onDismissRequest = { showReportDialog = false },
                             title = { Text("Denunciar usuario") },
                             text = { Text("¿Confirmas que quieres denunciar a este usuario por contenido inapropiado o spam?") },
                             confirmButton = {
                                 TextButton(onClick = {
                                     viewModel.reportUser(targetUserId, "Inapropiado/Spam")
                                     showReportDialog = false
                                     onBack()
                                 }) { Text("Denunciar", color = MaterialTheme.colorScheme.error) }
                             },
                             dismissButton = {
                                 TextButton(onClick = { showReportDialog = false }) { Text("Cancelar") }
                             }
                         )
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
                                Box(contentAlignment = androidx.compose.ui.Alignment.TopStart) {
                                    AsyncImage(
                                        model = profile.profilePictureUrl,
                                        contentDescription = "Avatar",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .background(Color.Gray, androidx.compose.foundation.shape.CircleShape)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .then(
                                                if (profile.isGold) Modifier.border(
                                                    2.dp,
                                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                                        listOf(Color(0xFFFFD700), Color(0xFFFFA000))
                                                    ),
                                                    androidx.compose.foundation.shape.CircleShape
                                                ) else Modifier
                                            )
                                            .clickable { showExpandedAvatar = true }, // 🔍 Click para expandir
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    
                                    // 👑 Crown
                                    if (profile.isGold) {
                                        Text(
                                            "👑",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier
                                                .offset(x = (-4).dp, y = (-4).dp)
                                                .offset(x = (-4).dp, y = (-4).dp)
                                                .graphicsLayer(rotationZ = -15f)
                                                .zIndex(1f) // 🔝 Force on top
                                        )
                                    }
                                }
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
                                if (profile.isFounder) {
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                        color = androidx.compose.ui.graphics.Color(0xFF6200EA),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Text(
                                            "🚀 Founder", 
                                            color = Color.White, 
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(profile.nickname, style = MaterialTheme.typography.headlineSmall)
                                // Rating
                                if(profile.ratingCount > 0) {
                                    val avg = profile.ratingSum / profile.ratingCount
                                    Text("⭐ ${String.format("%.1f", avg)} (${profile.ratingCount})", color = Color(0xFFFFC107))
                                }
                            }
                        }

                        // 📊 ASISTENCIA (Round 14)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                        Text(profile.eventsRequestedCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        Text("Solicitudes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                        Text(profile.eventsAttendedCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        Text("Asistencias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                
                                if (profile.eventsRequestedCount > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val reliability = (profile.eventsAttendedCount.toFloat() / profile.eventsRequestedCount.toFloat() * 100).toInt()
                                    Text(
                                        "Tasa de compromiso: $reliability%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (reliability >= 80) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
                                    )
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

                        // Photos header
                        if (profile.photos.isNotEmpty()) {
                            Text("Fotos", style = MaterialTheme.typography.titleMedium)
                            
                            // State for Gallery
                            var selectedGalleryPhoto by remember { mutableStateOf<String?>(null) }

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
                                            .background(Color.LightGray)
                                            .clickable { selectedGalleryPhoto = photoUrl }, // 🔍 Click to view
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }
                            
                            // 🔍 Full Screen Gallery Viewer
                            if (selectedGalleryPhoto != null) {
                                androidx.compose.ui.window.Dialog(
                                    onDismissRequest = { selectedGalleryPhoto = null }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black)
                                            .clickable { selectedGalleryPhoto = null }, // Tap anywhere to close
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = selectedGalleryPhoto,
                                            contentDescription = "Foto Completa",
                                            modifier = Modifier.fillMaxWidth(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    }
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
