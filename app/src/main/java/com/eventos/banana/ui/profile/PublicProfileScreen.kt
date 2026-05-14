package com.eventos.banana.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border // ➕
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex // ➕
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.MoreVert
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    targetUserId: String,
    onBack: () -> Unit,
    onMessageClick: (String) -> Unit = {},  // userId to start chat with
    isCurrentUserVerified: Boolean = false, // 🔒 Restriction check
    viewModel: PublicProfileViewModel = hiltViewModel<PublicProfileViewModel, PublicProfileViewModel.Factory>(
        creationCallback = { factory -> factory.create(targetUserId) }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 🎯 Guide overlay (first visit only)
    val sharedPrefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("banana_prefs", android.content.Context.MODE_PRIVATE)
    val hasSeenProfileGuide = remember { mutableStateOf(
        sharedPrefs.getBoolean("profile_guide_seen", false)
    ) }

    // 🛡️ Track blocked state reactively from ViewModel
    val isBlocked by viewModel.isBlocked.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.profile?.nickname ?: stringResource(com.eventos.banana.R.string.profile_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    var showReportDialog by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = stringResource(com.eventos.banana.R.string.public_profile_options))
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(com.eventos.banana.R.string.public_profile_report_user)) },
                            onClick = { 
                                showMenu = false 
                                showReportDialog = true
                            }
                        )
                        if (isBlocked) {
                            // Show unblock option
                            DropdownMenuItem(
                                text = { Text("Desbloquear usuario") },
                                onClick = { 
                                    showMenu = false
                                    viewModel.unblockUser(targetUserId)
                                    android.widget.Toast.makeText(context, "✅ Usuario desbloqueado", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            // Show block option
                            DropdownMenuItem(
                                text = { Text(stringResource(com.eventos.banana.R.string.public_profile_block_user)) },
                                onClick = { 
                                    showMenu = false
                                    viewModel.blockUser(targetUserId)
                                    android.widget.Toast.makeText(context, "🚫 Usuario bloqueado", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                    
                    if (showReportDialog) {
                         // Simple Report Dialog
                         AlertDialog(
                             onDismissRequest = { showReportDialog = false },
                             title = { Text(stringResource(com.eventos.banana.R.string.public_profile_report_title)) },
                             text = { Text(stringResource(com.eventos.banana.R.string.public_profile_report_body)) },
                             confirmButton = {
                                 TextButton(onClick = {
                                     viewModel.reportUser(targetUserId, "Inapropiado/Spam")
                                     showReportDialog = false
                                     onBack()
                                 }) { Text(stringResource(com.eventos.banana.R.string.public_profile_report_confirm), color = MaterialTheme.colorScheme.error) }
                             },
                             dismissButton = {
                                 TextButton(onClick = { showReportDialog = false }) { Text(stringResource(com.eventos.banana.R.string.common_cancel)) }
                             }
                         )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .animateContentSize()
        ) {
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    com.eventos.banana.ui.components.SkeletonCircle(120.dp)
                    Spacer(Modifier.height(24.dp))
                    com.eventos.banana.ui.components.SkeletonTextLine(180.dp)
                    Spacer(Modifier.height(12.dp))
                    com.eventos.banana.ui.components.SkeletonTextLine(120.dp)
                    Spacer(Modifier.height(12.dp))
                    com.eventos.banana.ui.components.SkeletonTextLine(150.dp)
                    
                    var showFallback by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2000)
                        showFallback = true
                    }
                    if (showFallback) {
                        Spacer(Modifier.height(24.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                val profile = uiState.profile
                if (profile != null) {
                    // Check if it's the Ghost Profile (Deleted/Not Found)
                    val isDeletedAccount = profile.nickname == "Usuario" && profile.aboutMe == "Perfil no disponible"
                    
                    if (isDeletedAccount) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("👻", style = MaterialTheme.typography.displayLarge)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Cuenta no disponible",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Este usuario ha eliminado su cuenta o el perfil ya no existe.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onBack) {
                                Text("Volver")
                            }
                        }
                    } else {
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
                                            contentDescription = stringResource(com.eventos.banana.R.string.public_profile_full_avatar),
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
                        
                        // 🏆 GAMIFICATION SCORE
                        if (profile.score > 0) {
                            com.eventos.banana.ui.components.BananaCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("🏆", style = MaterialTheme.typography.headlineMedium)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "${profile.score} Pts",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            "Puntuación de comportamiento",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
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
                                        Text(stringResource(com.eventos.banana.R.string.public_profile_requests), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                        Text(profile.eventsAttendedCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        Text(stringResource(com.eventos.banana.R.string.public_profile_attendances), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                
                                if (profile.eventsRequestedCount > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val reliability = (profile.eventsAttendedCount.toFloat() / profile.eventsRequestedCount.toFloat() * 100).toInt()
                                    Text(
                                        stringResource(com.eventos.banana.R.string.public_profile_commitment_rate, reliability),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (reliability >= 80) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                        
                        // Social Action Buttons
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            when (uiState.friendStatus) {
                                FriendStatus.NONE -> {
                                    Button(
                                        onClick = { viewModel.sendFriendRequest(profile.uid) },
                                        modifier = Modifier.defaultMinSize(minWidth = 120.dp).height(48.dp)
                                    ) {
                                        Text(stringResource(com.eventos.banana.R.string.public_profile_add_friend))
                                    }
                                }
                                FriendStatus.REQUEST_SENT -> {
                                    Button(
                                        onClick = {}, 
                                        enabled = false, 
                                        modifier = Modifier.defaultMinSize(minWidth = 120.dp).height(48.dp)
                                    ) {
                                        Text(stringResource(com.eventos.banana.R.string.public_profile_request_sent))
                                    }
                                }
                                FriendStatus.REQUEST_RECEIVED -> {
                                    Button(
                                        onClick = { viewModel.acceptFriendRequest(profile.uid) },
                                        modifier = Modifier.defaultMinSize(minWidth = 120.dp).height(48.dp)
                                    ) {
                                        Text(stringResource(com.eventos.banana.R.string.public_profile_accept_request))
                                    }
                                }
                                FriendStatus.FRIEND -> {
                                    val isTargetVerified = profile.isVerified
                                    if (isCurrentUserVerified && isTargetVerified) {
                                        Button(
                                            onClick = { onMessageClick(profile.uid) },
                                            modifier = Modifier.defaultMinSize(minWidth = 120.dp).height(48.dp)
                                        ) {
                                            Text(stringResource(com.eventos.banana.R.string.public_profile_send_message))
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {}, 
                                            enabled = false,
                                            modifier = Modifier.defaultMinSize(minWidth = 120.dp).height(48.dp)
                                        ) {
                                            val reason = if (!isCurrentUserVerified) stringResource(com.eventos.banana.R.string.public_profile_verify_to_message) else stringResource(com.eventos.banana.R.string.public_profile_user_not_verified)
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
                                            Text(stringResource(com.eventos.banana.R.string.public_profile_this_is_you), color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                            
                            // Block/Unblock Button
                            if (uiState.friendStatus != FriendStatus.SELF) {
                                OutlinedButton(
                                    onClick = {
                                        if (isBlocked) {
                                            viewModel.unblockUser(targetUserId)
                                        } else {
                                            viewModel.blockUser(targetUserId)
                                        }
                                    },
                                    modifier = Modifier.defaultMinSize(minWidth = 120.dp).height(48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(if (isBlocked) "Desbloquear" else stringResource(com.eventos.banana.R.string.public_profile_block_user))
                                }
                            }
                        }

                        // Removed standalone Message Button block to integrate above


                        HorizontalDivider()

                        // About Me
                        if (profile.aboutMe.isNotBlank()) {
                            Text(stringResource(com.eventos.banana.R.string.public_profile_about_me), style = MaterialTheme.typography.titleMedium)
                            Text(profile.aboutMe, style = MaterialTheme.typography.bodyMedium)
                        }

                        // Interests
                        if (profile.interests.isNotEmpty()) {
                            Text(stringResource(com.eventos.banana.R.string.public_profile_interests), style = MaterialTheme.typography.titleMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                profile.interests.forEach {
                                    AssistChip(
                                        modifier = Modifier.defaultMinSize(minWidth = 120.dp).height(48.dp),
                                        onClick = {}, 
                                        label = { Text(it) }
                                    )
                                }
                            }
                        }

                        // Photos header
                        if (profile.photos.isNotEmpty()) {
                            Text(stringResource(com.eventos.banana.R.string.public_profile_photos), style = MaterialTheme.typography.titleMedium)
                            
                            // State for Gallery
                            var selectedGalleryPhoto by remember { mutableStateOf<String?>(null) }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            contentDescription = stringResource(com.eventos.banana.R.string.public_profile_full_photo),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        } // ends if(profile.photos.isNotEmpty())
                    } // ends Column
                } // ends else (Ghost Profile)
                } else if (uiState.error != null) {
                    Text("Error: ${uiState.error}", modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                }
            }
        }
    }

    // Show profile guide overlay on first visit
    if (!hasSeenProfileGuide.value) {
        ProfileGuideOverlay(
            onDismiss = {
                hasSeenProfileGuide.value = true
                sharedPrefs.edit().putBoolean("profile_guide_seen", true).apply()
            }
        )
    }
}
