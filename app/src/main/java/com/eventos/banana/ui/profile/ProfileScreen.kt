package com.eventos.banana.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer // ➕
import androidx.compose.ui.zIndex // ➕
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource // ➕
import com.eventos.banana.ui.util.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventos.banana.util.LocationHelper
import com.eventos.banana.viewmodel.ProfileUiState
import com.eventos.banana.viewmodel.ProfileViewModel
import com.eventos.banana.viewmodel.SessionViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import coil.compose.AsyncImage
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit,
    onFriendsClick: () -> Unit,
    onEventClick: (String) -> Unit = {},
    onProfileViewsClick: () -> Unit = {}, // 👁️ New Feature
    onLeaderboardClick: () -> Unit = {}, // 🏆 Gamification
    onSettingsClick: () -> Unit, // ⚙️ Updated
    profileViewModel: ProfileViewModel = viewModel()
) {
    val profileUiState by sessionViewModel.profileUiState.collectAsState()
    val uiState by profileViewModel.uiState.collectAsState()
    
    val historyEvents by profileViewModel.historyEvents.collectAsState()
    val savedEvents by profileViewModel.savedEvents.collectAsState()

    val profile = profileUiState.profile

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(profile?.savedEventIds, profile?.uid) {
        if (profile != null) {
            profileViewModel.loadUserEvents(profile.uid, profile.savedEventIds)
        }
    }

    // ================= ESTADO LOCAL =================
    var nickname by remember(profile?.nickname) {
        mutableStateOf(profile?.nickname ?: "")
    }

    var detectedRegion by remember { mutableStateOf<String?>(null) }
    var detectedCommune by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Image Picker (Safe Fallback)
    var pickingAvatar by remember { mutableStateOf(false) }
    var pickingCover by remember { mutableStateOf(false) } // 🆕 Cover Photo State
    var viewingImageUrl by remember { mutableStateOf<String?>(null) } // 🔍 Full Screen Viewer State
    
    // 📸 Upload progress
    val isUploadingPhoto by profileViewModel.isUploadingPhoto.collectAsState()
    var showCoverRatioDialog by remember { mutableStateOf(false) }

    // ✂️ Crop launcher — receives cropped image, uploads it
    val cropLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = com.canhub.cropper.CropImageContract(),
        onResult = { result ->
            if (result.isSuccessful && profile != null) {
                scope.launch {
                    try {
                        val croppedUri = result.uriContent
                        if (croppedUri != null) {
                            val bytes = context.contentResolver.openInputStream(croppedUri)?.use { it.readBytes() }
                            if (bytes != null) {
                                val uid = sessionViewModel.currentUserId() ?: return@launch
                                profileViewModel.uploadPhoto(
                                    uid,
                                    bytes,
                                    isProfilePicture = pickingAvatar,
                                    isCoverPhoto = pickingCover
                                )
                            } else {
                                snackbarHostState.showSnackbar("Error: No se pudo leer la imagen recortada")
                            }
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error al procesar imagen: ${e.message}")
                    } finally {
                        pickingAvatar = false
                        pickingCover = false
                    }
                }
            } else {
                pickingAvatar = false
                pickingCover = false
            }
        }
    )

    // Helper: launch crop with appropriate settings
    fun launchCrop(
        isAvatar: Boolean = false, 
        isCover: Boolean = false,
        ratioX: Int = 1,
        ratioY: Int = 1
    ) {
        pickingAvatar = isAvatar
        pickingCover = isCover
        val options = com.canhub.cropper.CropImageContractOptions(
            uri = null, // Let user pick source
            cropImageOptions = com.canhub.cropper.CropImageOptions(
                imageSourceIncludeCamera = false,
                imageSourceIncludeGallery = true,
                guidelines = com.canhub.cropper.CropImageView.Guidelines.ON,
                cropShape = if (isAvatar) com.canhub.cropper.CropImageView.CropShape.OVAL 
                           else com.canhub.cropper.CropImageView.CropShape.RECTANGLE,
                fixAspectRatio = isAvatar || isCover,
                aspectRatioX = if (isAvatar) 1 else if (isCover) ratioX else 1,
                aspectRatioY = if (isAvatar) 1 else if (isCover) ratioY else 1,
                outputCompressQuality = 80,
                maxCropResultWidth = if (isAvatar) 800 else 1600,
                maxCropResultHeight = if (isAvatar) 800 else if (isCover) 1600 else 1600,
                activityTitle = when {
                    isAvatar -> "Recortar foto de perfil"
                    isCover -> "Recortar portada"
                    else -> "Recortar foto"
                },
                toolbarColor = android.graphics.Color.BLACK,
                activityBackgroundColor = android.graphics.Color.BLACK,
                toolbarTintColor = android.graphics.Color.WHITE
            )
        )
        cropLauncher.launch(options)
    }

    // Config Section State
    var isConfigExpanded by remember { mutableStateOf(false) }
    var isNotificationsExpanded by remember { mutableStateOf(false) }

    // ================= VALIDACIONES =================
    val canSaveNickname =
        profile != null &&
                nickname.isNotBlank() &&
                nickname != profile.nickname

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.eventos.banana.R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = stringResource(com.eventos.banana.R.string.settings_title))
                    }
                }
            )
        }
    ) { padding ->

        if (profile == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp), // Fixed bottom padding (Scaffold handles insets)
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // ================= SUCCESS HANDLING =================
                LaunchedEffect(uiState) {
                    if (uiState is ProfileUiState.Success) {
                        detectedRegion = null
                        detectedCommune = null
                        snackbarHostState.showSnackbar("Cambios guardados correctamente ✅")
                    } else if (uiState is ProfileUiState.Error) {
                        snackbarHostState.showSnackbar("Error: ${(uiState as ProfileUiState.Error).message}")
                    }
                }

                if (profileUiState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // 1. HEADER (Cover + Avatar + Nickname)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp) // Taller header for premium feel
                ) {
                    // --- COVER PHOTO ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (!profile.coverPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(profile.coverPhotoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(com.eventos.banana.R.string.profile_cover_desc),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { viewingImageUrl = profile.coverPhotoUrl },
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            
                            // Dark Gradient Overlay for text readability (if we had text) & button visibility
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                                            startY = 0f,
                                            endY = 500f
                                        )
                                    )
                            )
                        } else {
                            // Friendly Placeholder
                            Box(
                                Modifier.fillMaxSize().background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                                        )
                                    )
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(stringResource(com.eventos.banana.R.string.profile_add_cover), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                }
                            }
                        }

                        // Edit Cover Button (Top Right)
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable { 
                                    showCoverRatioDialog = true
                                }
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                                contentDescription = stringResource(com.eventos.banana.R.string.profile_edit_cover),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 50.dp), // POP OUT: Half outside cover
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarSize = 140.dp
                        val borderSize = if (profile.isGold) 6.dp else 4.dp
                        val borderColor = if (profile.isGold) androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA000))) 
                                          else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surface)

                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(borderColor)
                                .padding(borderSize)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.surface) // Fallback background
                        ) {
                            if (!profile.profilePictureUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                                        .data(profile.profilePictureUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = stringResource(com.eventos.banana.R.string.profile_avatar_desc),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { viewingImageUrl = profile.profilePictureUrl },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { 
                                            launchCrop(isAvatar = true)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Person,
                                        contentDescription = stringResource(com.eventos.banana.R.string.profile_add_photo),
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        // Edit Avatar Badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-6).dp, y = (-6).dp)
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { 
                                        launchCrop(isAvatar = true)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                                        contentDescription = stringResource(com.eventos.banana.R.string.profile_edit_avatar),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // 👑 GOLD MARKER
                    if (profile.isGold) {
                         Text(
                            text = "👑",
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(x = (-50).dp, y = (-80).dp) // Adjust position relative to avatar
                                .graphicsLayer(rotationZ = -20f)
                                .zIndex(1f)
                        )
                    }

                    // 📸 Upload Progress Overlay
                    if (isUploadingPhoto) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    "Subiendo foto...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                if (showCoverRatioDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showCoverRatioDialog = false },
                        title = { Text("¿Cómo quieres recortar tu portada?") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        showCoverRatioDialog = false
                                        launchCrop(isCover = true, ratioX = 4, ratioY = 3)
                                    }
                                ) {
                                    Text("Estándar (4:3) - Recomendado")
                                }
                                androidx.compose.material3.OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        showCoverRatioDialog = false
                                        launchCrop(isCover = true, ratioX = 16, ratioY = 9)
                                    }
                                ) {
                                    Text("Panorámico (16:9)")
                                }
                                androidx.compose.material3.OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        showCoverRatioDialog = false
                                        launchCrop(isCover = true, ratioX = 1, ratioY = 1)
                                    }
                                ) {
                                    Text("Cuadrado (1:1)")
                                }
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { showCoverRatioDialog = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }

                // Spacing after the popped-out avatar
                Spacer(modifier = Modifier.height(55.dp))

                // 2. USER INFO SECTION
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // FOUNDER / GOLD BADGE
                    if (profile.isFounder) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = androidx.compose.ui.graphics.Color(0xFF6200EA),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                stringResource(com.eventos.banana.R.string.profile_founder_edition), 
                                color = Color.White, 
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // NICKNAME
                    var showEditNameDialog by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clickable { showEditNameDialog = true }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = profile.nickname.ifBlank { stringResource(com.eventos.banana.R.string.profile_no_name) },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (sessionViewModel.isEmailVerified) {
                        Text(
                            stringResource(com.eventos.banana.R.string.profile_verified), 
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                     // EDIT NAME DIALOG
                    if (showEditNameDialog) {
                        var tempName by remember { mutableStateOf(profile.nickname) }
                        AlertDialog(
                            onDismissRequest = { showEditNameDialog = false },
                            title = { Text(stringResource(com.eventos.banana.R.string.profile_edit_nickname_title)) },
                            text = {
                                OutlinedTextField(
                                    value = tempName,
                                    onValueChange = { tempName = it },
                                    singleLine = true,
                                    label = { Text(stringResource(com.eventos.banana.R.string.profile_new_nickname_label)) }
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val uid = sessionViewModel.currentUserId() 
                                        if (uid != null && tempName.isNotBlank()) {
                                            profileViewModel.updateNickname(uid, tempName)
                                            showEditNameDialog = false
                                        }
                                    }
                                ) { Text(stringResource(com.eventos.banana.R.string.common_save)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showEditNameDialog = false }) { Text(stringResource(com.eventos.banana.R.string.common_cancel)) }
                            }
                        )
                    }
                }

                // 3. STATS CARD
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(0.dp) // Flat but distinct background
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         // Amigos
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = profile.friends.size.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(stringResource(com.eventos.banana.R.string.profile_friends), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Box(Modifier.height(24.dp).width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))

                        // Asistidos
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = profile.eventsAttendedCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(stringResource(com.eventos.banana.R.string.profile_attended), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Box(Modifier.height(24.dp).width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))

                        // Creados
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = profile.eventsCreatedLifetime.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(stringResource(com.eventos.banana.R.string.profile_created), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }



                // 🏆 LEADERBOARD BUTTON
                OutlinedButton(
                    onClick = onLeaderboardClick,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("🏆 Ver Ranking Global")
                }

                // 4. REPUTATION & RELIABILITY
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Reputación
                    com.eventos.banana.ui.components.BananaCard(
                         containerColor = when {
                            profile.isPerfectAttendee() -> MaterialTheme.colorScheme.tertiaryContainer
                            profile.averageRating >= 4.5 -> MaterialTheme.colorScheme.primaryContainer
                            profile.averageRating >= 4.0 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.getRatingBadge(),
                                style = MaterialTheme.typography.displayMedium,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Column {
                                Text(
                                    text = profile.localizedBadgeText(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "⭐ ${String.format("%.1f", profile.averageRating)} " + stringResource(com.eventos.banana.R.string.profile_ratings_count_fmt, profile.ratingCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Confiabilidad (Progress)
                    com.eventos.banana.ui.components.BananaCard {
                        val approved = profile.eventsRequestedCount.coerceAtLeast(1)
                        val attended = profile.eventsAttendedCount
                        val effectiveApproved = if (attended > approved) attended else approved
                        val percentage = if (effectiveApproved > 0) attended.toFloat() / effectiveApproved.toFloat() else 0f
                        
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(com.eventos.banana.R.string.profile_reliability), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${(percentage * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (percentage >= 0.8f) Color(0xFF4CAF50) else Color(0xFFFFC107)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { percentage },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                color = if (percentage >= 0.8f) Color(0xFF4CAF50) else Color(0xFFFFC107),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                stringResource(com.eventos.banana.R.string.profile_reliability_desc, attended, effectiveApproved),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } // 3. SOCIAL PROFILE
                com.eventos.banana.ui.components.BananaCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                     // Removed manual padding of 16.dp since BananaCard provides it
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(com.eventos.banana.R.string.profile_social_title), style = MaterialTheme.typography.titleMedium)

                        var aboutMe by remember(profile.aboutMe) { mutableStateOf(profile.aboutMe) }
                        OutlinedTextField(
                            value = aboutMe,
                            onValueChange = { aboutMe = it },
                            label = { Text(stringResource(com.eventos.banana.R.string.profile_about_me)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )

                        Text(stringResource(com.eventos.banana.R.string.profile_interests), style = MaterialTheme.typography.bodyMedium)
                        var newInterest by remember { mutableStateOf("") }
                        var interests by remember(profile.interests) { mutableStateOf(profile.interests) }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newInterest,
                                onValueChange = { newInterest = it },
                                label = { Text(stringResource(com.eventos.banana.R.string.profile_new_interest)) },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (newInterest.isNotBlank() && !interests.contains(newInterest.trim())) {
                                    interests = interests + newInterest.trim()
                                    newInterest = ""
                                }
                            }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Add, null)
                            }
                        }

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            interests.forEach { interest ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(interest) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp).clickable { interests = interests - interest }
                                        )
                                    }
                                )
                            }
                        }
                        
                        // SUGGESTED INTERESTS (Subcategories)
                        Text(stringResource(com.eventos.banana.R.string.profile_suggestions), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        
                        com.eventos.banana.domain.model.EventType.values().forEach { type ->
                            if (type != com.eventos.banana.domain.model.EventType.OTRO) {
                                var expanded by remember { mutableStateOf(false) }
                                
                                Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expanded = !expanded }
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${type.emoji} ${type.localizedName()}", 
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            if (expanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    AnimatedVisibility(visible = expanded) {
                                        @OptIn(ExperimentalLayoutApi::class)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        ) {
                                            val localizedSubs = type.localizedSubcategories()
                                            val originalSubs = type.subcategories
                                            localizedSubs.forEachIndexed { index, localizedSub ->
                                                val originalKey = originalSubs[index]
                                                val isSelected = interests.contains(originalKey)
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        interests = if (isSelected) {
                                                            interests - originalKey
                                                        } else {
                                                            interests + originalKey
                                                        }
                                                    },
                                                    label = { Text(localizedSub) },
                                                    leadingIcon = if (isSelected) {
                                                        { Icon(androidx.compose.material.icons.Icons.Default.Check, null, Modifier.size(16.dp)) }
                                                    } else null
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                         com.eventos.banana.ui.components.BananaButton(
                            onClick = {
                                val uid = sessionViewModel.currentUserId() ?: return@BananaButton
                                profileViewModel.updateSocialProfile(uid, aboutMe, interests)
                            },
                            text = stringResource(com.eventos.banana.R.string.profile_update_social),
                            enabled = aboutMe != profile.aboutMe || interests != profile.interests,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 4. PHOTOS (Galeria)
                com.eventos.banana.ui.components.BananaCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column { // Removed manual padding
                        Text(stringResource(com.eventos.banana.R.string.profile_photos), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val maxPhotos = 6
                            val currentPhotos = profile.photos
                            
                            repeat(maxPhotos) { index ->
                                val photoUrl = currentPhotos.getOrNull(index)
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            if (photoUrl == null) {
                                                launchCrop()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (photoUrl != null) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        
                                        // Delete Button
                                        Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                                contentDescription = stringResource(com.eventos.banana.R.string.profile_delete_photo),
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(Color.Red.copy(alpha=0.8f), androidx.compose.foundation.shape.CircleShape)
                                                    .padding(2.dp)
                                                    .clickable { 
                                                        val uid = sessionViewModel.currentUserId() ?: return@clickable
                                                        profileViewModel.deletePhoto(uid, photoUrl) 
                                                    }
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.Add,
                                            contentDescription = stringResource(com.eventos.banana.R.string.profile_add_photo),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4.5 MIS EVENTOS (History & Saved)
                com.eventos.banana.ui.components.BananaCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column { // Removed padding(16.dp)
                        Text(stringResource(com.eventos.banana.R.string.profile_my_events), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        var selectedEventTab by remember { mutableIntStateOf(0) }
                        TabRow(selectedTabIndex = selectedEventTab) {
                            Tab(selected = selectedEventTab == 0, onClick = { selectedEventTab = 0 }, text = { Text(stringResource(com.eventos.banana.R.string.profile_tab_history)) })
                            Tab(selected = selectedEventTab == 1, onClick = { selectedEventTab = 1 }, text = { Text(stringResource(com.eventos.banana.R.string.profile_tab_saved)) })
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        val eventsToShow = if (selectedEventTab == 0) historyEvents else savedEvents
                        
                        if (eventsToShow.isEmpty()) {
                            Text(
                                text = if (selectedEventTab == 0) stringResource(com.eventos.banana.R.string.profile_no_history) else stringResource(com.eventos.banana.R.string.profile_no_saved),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                eventsToShow.forEach { event ->
                                    com.eventos.banana.ui.components.BananaCard(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { onEventClick(event.id) },
                                        contentPadding = 12.dp // Using 12.dp to match original inner padding preference
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(), // Removed padding(12.dp)
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text(
                                                    java.text.SimpleDateFormat("dd MMM", java.util.Locale.forLanguageTag("es")).format(java.util.Date(event.startAt)), 
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            
                                            // Save Toggle
                                            val isSaved = profile.savedEventIds.contains(event.id)
                                            IconButton(onClick = { 
                                                profileViewModel.toggleSaveEvent(profile.uid, event.id, profile.savedEventIds) 
                                            }) {
                                                Icon(
                                                    imageVector = if (isSaved) androidx.compose.material.icons.Icons.Filled.Star else androidx.compose.material.icons.Icons.Filled.Add,
                                                    contentDescription = "Guardar",
                                                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Instagram Link (Moved back here)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            // 📸 Open Instagram profile: somosbananaapp
                            val username = "somosbananaapp"
                            val webUri = android.net.Uri.parse("https://www.instagram.com/$username/")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri)
                            intent.setPackage("com.instagram.android")
                            
                            // Check if Instagram app can handle this intent
                            try {
                                context.startActivity(intent)
                            } catch (e: android.content.ActivityNotFoundException) {
                                // Fallback: open in browser without package restriction
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, webUri))
                            }
                        }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(com.eventos.banana.R.string.profile_instagram_follow), 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
            }
        }

        // 🔍 Full Screen Viewer
        if (viewingImageUrl != null) {
            FullScreenImageViewer(imageUrl = viewingImageUrl!!, onDismiss = { viewingImageUrl = null })
        }
    }


@Composable
fun FullScreenImageViewer(imageUrl: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false, // Full screen
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() }, // Click anywhere to close
            contentAlignment = Alignment.Center
        ) {
            // 1. BLURRED BACKGROUND (Premium Effect)
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.6f)
                    .blur(radius = 30.dp), // Blur effect
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            
            // 2. DARK SCRIM
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )

            // 3. MAIN IMAGE (Fit)
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Full Screen",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            
            // 4. CLOSE BUTTON
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                contentDescription = "Cerrar",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding() // Safe margin for status bar
                    .padding(top = 16.dp, end = 16.dp)
                    .size(48.dp) // Larger hit target
                    .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                    .padding(12.dp)
                    .clickable { onDismiss() }
            )
        }
    }
}
