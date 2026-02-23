package com.eventos.banana.ui.event

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.eventos.banana.ui.components.shimmerEffect
import com.eventos.banana.ui.components.SuccessOverlay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import coil.compose.AsyncImage
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventStatus

@OptIn(ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    onRateParticipants: (Event) -> Unit,
    onBoostClick: () -> Unit,
    initialTab: Int = 0,
    eventState: com.eventos.banana.domain.model.EventDetailUiState, // Pass full state to access nicknames
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {},
    hasAttended: Boolean = false,
    checkInState: com.eventos.banana.ui.event.CheckInState = com.eventos.banana.ui.event.CheckInState.Idle,
    onCheckInClick: () -> Unit = {},
    onResetCheckInState: () -> Unit = {},
    actionState: com.eventos.banana.ui.event.ActionState = com.eventos.banana.ui.event.ActionState.Idle,
    onResetActionState: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val isCreator = event.creatorId == currentUserId
    val isApproved = event.approvedParticipants.contains(currentUserId)
    val isPending = event.pendingRequests.any { it.userId == currentUserId }
    val isRejected = event.rejectedParticipants.contains(currentUserId)
    
    // 🌍 Public Events Visibility Rules
    val canSeeFeed = isCreator || isApproved || event.isPublic

    // Guide State
    val sharedPreferences = remember { context.getSharedPreferences("banana_prefs", android.content.Context.MODE_PRIVATE) }
    var showGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!sharedPreferences.getBoolean("event_detail_guide_seen", false)) {
            showGuide = true
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle Action Results
    LaunchedEffect(actionState) {
        when (actionState) {
            is com.eventos.banana.ui.event.ActionState.Success -> {
                snackbarHostState.showSnackbar(actionState.message)
                onResetActionState()
            }
            is com.eventos.banana.ui.event.ActionState.Error -> {
                snackbarHostState.showSnackbar(actionState.message)
                onResetActionState()
            }
            else -> {}
        }
    }

    var selectedTab by remember { mutableStateOf(initialTab) }
    val tabs = listOf("Detalles", "Muro")

    // 📏 Header Measurement
    var headerHeightPx by remember { mutableStateOf(0f) }
    val headerHeightDp = with(LocalDensity.current) { headerHeightPx.toDp() }
    
    // 🎨 Dynamic Header Style
    val isDetailsTab = selectedTab == 0
    val headerBackground = if (isDetailsTab) {
        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f) // ✨ Transparent for Image
    } else {
        MaterialTheme.colorScheme.surface // Opaque for Wall
    }

    Box(modifier = Modifier.fillMaxSize()) {
        
        // ---------- CONTENT BUFFER (Behind Header) ----------
        // We render content FIRST so it sits behind the floating header
        
        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                // DETALLES TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // ========== FOTO DEL EVENTO (Moved to Top) ==========
                    // Only show if available, otherwise show placeholder
                    val imageModifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp) // Taller to extend behind header
                    
                    if (!event.imageUrl.isNullOrBlank()) {
                        var showImageDialog by remember { mutableStateOf(false) }

                        Box(modifier = imageModifier) {
                            with(sharedTransitionScope) {
                                coil.compose.SubcomposeAsyncImage(
                                model = event.imageUrl,
                                contentDescription = "Foto del evento",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { showImageDialog = true },
                                contentScale = ContentScale.Crop,
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .shimmerEffect()
                                    )
                                }
                            )
                        }
                            
                            // Bottom Gradient for text readability (only at the bottom of the image)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                                            startY = 500f // Start lower
                                        )
                                    )
                            )
                            
                            // Full Screen Image Dialog Logic (Preserved)
                             if (showImageDialog) {
                                androidx.compose.ui.window.Dialog(
                                    onDismissRequest = { showImageDialog = false },
                                    properties = androidx.compose.ui.window.DialogProperties(
                                        usePlatformDefaultWidth = false 
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(androidx.compose.ui.graphics.Color.Black)
                                    ) {
                                        AsyncImage(
                                            model = event.imageUrl,
                                            contentDescription = "Foto completa",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable { showImageDialog = false },
                                            contentScale = ContentScale.Fit
                                        )
                                        IconButton(
                                            onClick = { showImageDialog = false },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cerrar",
                                                tint = androidx.compose.ui.graphics.Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Placeholder
                         Box(
                             modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                             contentAlignment = Alignment.Center
                        ) {
                            Text("🍌", style = MaterialTheme.typography.displayLarge)
                        }
                    }

                    // ---------- REST OF DETAILS CONTENT ----------
                    // The rest of the content follows naturally
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                         // ... Content continues ...
                    // Title (moved here if image covered it, or redundant)
                    // Let's keep title big here
                    
                    // Date & Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        val dateFormat = java.text.SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale.forLanguageTag("es-ES"))
                        val dateStr = dateFormat.format(java.util.Date(event.startAt))
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        Text(dateStr, style = MaterialTheme.typography.bodyLarge)
                    }

                    // Location
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${event.commune}, ${event.region}", 
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Creator Card (Refined)
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onUserClick(event.creatorId) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        // elevation = CardDefaults.cardElevation(2.dp)
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
                            
                            val creatorProfile = (eventState as? com.eventos.banana.domain.model.EventDetailUiState.Success)
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
                                    androidx.compose.ui.graphics.Color(0xFFFFD700).copy(alpha = 0.2f) // Gold tint
                                else 
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (event.isBoosted) androidx.compose.ui.graphics.Color(0xFFFFD700) else MaterialTheme.colorScheme.outlineVariant
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
                                        color = if (event.isBoosted) androidx.compose.ui.graphics.Color(0xFFB8860B) else MaterialTheme.colorScheme.onSurface
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
                                            containerColor = androidx.compose.ui.graphics.Color(0xFFFFD700),
                                            contentColor = androidx.compose.ui.graphics.Color.Black
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
                                    val currentUserProfile = (eventState as? com.eventos.banana.domain.model.EventDetailUiState.Success)
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
                                            containerColor = androidx.compose.ui.graphics.Color(0xFFFFD700),
                                            contentColor = androidx.compose.ui.graphics.Color.Black
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


                    // ========== PARTICIPANTES ==========
                    if (event.approvedParticipants.isNotEmpty()) {
                        var showParticipantsDialog by remember { mutableStateOf(false) }
                        
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        if (isCreator || isApproved || event.isPublic) {
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
                                    Text(stringResource(com.eventos.banana.R.string.event_detail_participants, event.approvedParticipants.size))
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    ) {
                                        event.approvedParticipants.forEach { userId ->
                                            // Nickname lookup inside Dialog
                                            val participantProfile = (eventState as? com.eventos.banana.domain.model.EventDetailUiState.Success)
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
                                                            .clip(androidx.compose.foundation.shape.CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(androidx.compose.foundation.shape.CircleShape)
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

                    // ========== ENCUENTROS & PUNTUACIÓN (Round 12) ==========
                    if (event.status == EventStatus.OPEN || event.status == EventStatus.CLOSED) {
                        val canValidate = isCreator || isApproved
                        if (canValidate) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "📱 Encuentros & Puntuación",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    if (hasAttended || isCreator) {
                                        // ✅ COMPACT SUCCESS UI (Static - Visual Confirmation)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "✅ Check-in Realizado",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    } else {
                                        // 🔘 ORIGINAL BUTTONS (GPS Check-In)
                                        // Only show explanatory text if NOT checked in
                                        Text(
                                            "Para puntuar a otros asistentes, primero debes confirmar que estuviste ahí.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        // 🔘 ORIGINAL BUTTONS (GPS Check-In)
                                        val coroutineScope = rememberCoroutineScope()
                                        val geofenceManager = remember { com.eventos.banana.util.EventGeofenceManager(context) }
                                        var isVerifyingLocation by remember { mutableStateOf(false) }
                                        var currentDistance by remember { mutableStateOf<Int?>(null) }
                                        
                                        // Auto-check distance on load
                                        LaunchedEffect(Unit) {
                                             if (com.eventos.banana.util.LocationHelper.hasLocationPermissions(context) && 
                                                 com.eventos.banana.util.LocationHelper.isLocationEnabled(context)) {
                                                 currentDistance = geofenceManager.getDistanceToEvent(event)
                                             }
                                        }
                                        
                                        // Distance Indicator
                                        if (currentDistance != null) {
                                            val isCloseEnough = currentDistance!! <= 100
                                            Text(
                                                text = "📍 Distancia actual: $currentDistance m",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (isCloseEnough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        Spacer(Modifier.height(8.dp))

                                        OutlinedButton(
                                            onClick = { 
                                                // Check Permissions
                                                if (!com.eventos.banana.util.LocationHelper.hasLocationPermissions(context)) {
                                                    android.widget.Toast.makeText(context, "⚠️ Permiso de ubicación requerido", android.widget.Toast.LENGTH_LONG).show()
                                                    return@OutlinedButton
                                                }
                                                // Time Validation
                                                val now = System.currentTimeMillis()
                                                val oneHour = 3600000L
                                                if (event.startAt > 0 && now < (event.startAt - oneHour)) {
                                                    android.widget.Toast.makeText(context, "🕒 Muy temprano (espera al inicio)", android.widget.Toast.LENGTH_LONG).show()
                                                    return@OutlinedButton
                                                }
                                                if (event.endAt > 0 && now > (event.endAt + oneHour)) {
                                                    android.widget.Toast.makeText(context, "🕒 El evento ya finalizó", android.widget.Toast.LENGTH_LONG).show()
                                                    return@OutlinedButton
                                                }
                                                // GPS Enabled
                                                if (!com.eventos.banana.util.LocationHelper.isLocationEnabled(context)) {
                                                    android.widget.Toast.makeText(context, "⚠️ Enciende tu GPS", android.widget.Toast.LENGTH_LONG).show()
                                                    return@OutlinedButton
                                                }

                                                isVerifyingLocation = true
                                                coroutineScope.launch {
                                                    try {
                                                        // Update distance
                                                        currentDistance = geofenceManager.getDistanceToEvent(event)
                                                        val isAtEvent = geofenceManager.isUserAtEvent(event)
                                                        
                                                        if (isAtEvent) {
                                                            // Calls PARAMETER callback
                                                            onCheckInClick()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "❌ Debes estar a 100m del evento", android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    } finally {
                                                        isVerifyingLocation = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.primary
                                            ),
                                            enabled = !isVerifyingLocation && checkInState !is com.eventos.banana.ui.event.CheckInState.Loading
                                        ) {
                                            if (isVerifyingLocation || checkInState is com.eventos.banana.ui.event.CheckInState.Loading) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(com.eventos.banana.R.string.event_detail_verifying))
                                            } else {
                                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(com.eventos.banana.R.string.event_detail_gps_checkin))
                                            }
                                        }
                                        
                                        Spacer(Modifier.height(8.dp))
                                    }

                                    // STANDALONE NFC BUTTON REMOVED
                                }
                            }
                        }
                    }

                    // ========== UBICACIÓN EN MAPA ==========
                    if (event.exactLatitude != null && event.exactLongitude != null) {
                        val canSeeMap = isCreator || isApproved || event.isPublic
                        
                        if (canSeeMap) {
                            val eventLocation = com.google.android.gms.maps.model.LatLng(event.exactLatitude, event.exactLongitude)
                            val cameraPositionState = com.google.maps.android.compose.rememberCameraPositionState {
                                position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(eventLocation, 15f)
                            }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp) // Fixed height for embedded map
                                    .padding(vertical = 8.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column {
                                    // Title Header inside Card
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(com.eventos.banana.R.string.event_detail_exact_location),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.weight(1f))
                                        // "Open" hint text
                                        Text(stringResource(com.eventos.banana.R.string.event_detail_tap_to_navigate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        com.google.maps.android.compose.GoogleMap(
                                            modifier = Modifier.fillMaxSize(),
                                            cameraPositionState = cameraPositionState,
                                            uiSettings = com.google.maps.android.compose.MapUiSettings(
                                                zoomControlsEnabled = false,
                                                scrollGesturesEnabled = false,
                                                zoomGesturesEnabled = false,
                                                rotationGesturesEnabled = false,
                                                tiltGesturesEnabled = false,
                                                compassEnabled = false,
                                                myLocationButtonEnabled = false
                                            ),
                                            googleMapOptionsFactory = {
                                                com.google.android.gms.maps.GoogleMapOptions().liteMode(true)
                                            },
                                            onMapClick = {
                                               // Open External Map
                                               val uri = android.net.Uri.parse("geo:0,0?q=${event.exactLatitude},${event.exactLongitude}(${android.net.Uri.encode(event.title)})")
                                               val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                               intent.setPackage("com.google.android.apps.maps")
                                               try {
                                                   androidx.core.content.ContextCompat.startActivity(context, intent, null)
                                               } catch (e: Exception) {
                                                   val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                   androidx.core.content.ContextCompat.startActivity(context, fallbackIntent, null)
                                               }
                                            }
                                        ) {
                                            com.google.maps.android.compose.Marker(
                                                state = com.google.maps.android.compose.MarkerState(position = eventLocation),
                                                title = event.title,
                                                snippet = event.exactAddress
                                            )
                                        }
                                        
                                    // Overlay "How to get there" Button
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd) // Bottom Right
                                            .padding(12.dp)
                                    ) {
                                        ExtendedFloatingActionButton(
                                            onClick = {
                                                val uri = android.net.Uri.parse("geo:0,0?q=${event.exactLatitude},${event.exactLongitude}(${android.net.Uri.encode(event.title)})")
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                intent.setPackage("com.google.android.apps.maps")
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                    context.startActivity(fallbackIntent)
                                                } 
                                            },
                                            icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(com.eventos.banana.R.string.event_detail_navigate)) },
                                            text = { Text(stringResource(com.eventos.banana.R.string.event_detail_how_to_get_there)) },
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    
                                    // Transparent scrim for map interaction (keep existing)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable {
                                               val uri = android.net.Uri.parse("geo:0,0?q=${event.exactLatitude},${event.exactLongitude}(${android.net.Uri.encode(event.title)})")
                                               val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                               try {
                                                   context.startActivity(intent)
                                               } catch (e: Exception) { }
                                            }
                                    )
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

                HorizontalDivider()
                Text(event.description)
                HorizontalDivider()

                Text(stringResource(com.eventos.banana.R.string.event_detail_spots, event.approvedParticipants.size, event.maxParticipants))

                // ACCIONES USUARIO (NO CREADOR)
                if (!isCreator) {
                    when {
                        event.status == EventStatus.CANCELLED ->
                            DisabledButton(stringResource(com.eventos.banana.R.string.event_detail_event_cancelled))

                        event.status == EventStatus.CLOSED ->
                            DisabledButton(stringResource(com.eventos.banana.R.string.event_detail_event_closed))

                        isApproved ->
                            DisabledButton(stringResource(com.eventos.banana.R.string.event_detail_already_accepted))

                        isPending ->
                            DisabledButton(stringResource(com.eventos.banana.R.string.event_detail_request_sent))

                        isRejected ->
                            DisabledButton(stringResource(com.eventos.banana.R.string.event_detail_request_rejected))

                        else -> {
                            Button(
                                onClick = onJoinClick,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isJoining
                            ) {
                                Text(if (event.isPublic) stringResource(com.eventos.banana.R.string.event_detail_join_public) else stringResource(com.eventos.banana.R.string.event_detail_join_private))
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
                        Text(stringResource(com.eventos.banana.R.string.event_detail_close_event))
                    }

                    val cancelReason = stringResource(com.eventos.banana.R.string.event_detail_cancelled_reason)
                    OutlinedButton(
                        onClick = { onCancelEvent(cancelReason) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(com.eventos.banana.R.string.event_detail_cancel_event))
                    }
                }

                // ========== CONFIRMAR ENCUENTROS NFC (REMOVED - DUPLICATE) ==========
                // Logic moved to "Encuentros & Puntuación" section above


                // ========== CALIFICAR PARTICIPANTES (Round 11) ==========
                // Visible para creador y participantes aprobados, solo si el evento terminó
                val now = System.currentTimeMillis()
                val eventEnded = event.endAt < now || event.status == EventStatus.CLOSED
                val canRate = (isCreator || isApproved) && eventEnded
                val ratingDeadline = event.ratingDeadline ?: (event.endAt + 432000000L) // 5 días después
                val withinRatingWindow = now <= ratingDeadline

                if (canRate && withinRatingWindow) {
                    Spacer(Modifier.height(8.dp))
                    
                    // 🔒 VALIDACIÓN DE ASISTENCIA (Advertencia, no bloqueo)
                    if (!isCreator && !hasAttended) {
                         Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(com.eventos.banana.R.string.event_detail_unverified_title), fontWeight = FontWeight.Bold)
                                Text(stringResource(com.eventos.banana.R.string.event_detail_rate_requirement), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(com.eventos.banana.R.string.event_detail_rate_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            
                            val daysRemaining = ((ratingDeadline - now) / 86400000L).toInt()
                            Text(
                                stringResource(com.eventos.banana.R.string.event_detail_days_remaining, daysRemaining, if (daysRemaining == 1) stringResource(com.eventos.banana.R.string.event_detail_day) else stringResource(com.eventos.banana.R.string.event_detail_days)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Button(
                                onClick = { onRateParticipants(event) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(com.eventos.banana.R.string.event_detail_rate_now))
                            }
                        }
                    }
                }

                // SOLICITUDES PENDIENTES
                if (isCreator && event.pendingRequests.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(com.eventos.banana.R.string.event_detail_pending_requests), style = MaterialTheme.typography.titleMedium)

                    // ⚡ FAST PASS: Ordenar Gold primero
                    val sortedRequests = event.pendingRequests.sortedByDescending { request ->
                         val profile = (eventState as? com.eventos.banana.domain.model.EventDetailUiState.Success)
                                    ?.userProfiles?.get(request.userId)
                         profile?.isGold == true
                    }

                    sortedRequests.forEach { request ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {

                                val requesterProfile = (eventState as? com.eventos.banana.domain.model.EventDetailUiState.Success)
                                    ?.userProfiles?.get(request.userId)
                                val requesterName = requesterProfile?.nickname ?: "Usuario"
                                val badge = requesterProfile?.getRatingBadge() ?: ""
                                val isGold = requesterProfile?.isGold == true

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$requesterName $badge",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isGold) {
                                        Spacer(Modifier.width(4.dp))
                                        Text("👑", style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(4.dp))
                                        // Optional: "Fast Pass" label
                                        Surface(
                                            color = androidx.compose.ui.graphics.Color(0xFFFFD700),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                " FAST PASS ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = androidx.compose.ui.graphics.Color.Black,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                
                                // Stats (Punto 5)
                                if (requesterProfile != null) {
                                    val attended = requesterProfile.eventsAttendedCount
                                    val requested = requesterProfile.eventsRequestedCount
                                    val reliability = if (requested > 0) (attended * 100 / requested) else 0
                                    
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(com.eventos.banana.R.string.event_detail_stats_format, attended, requested, reliability),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        if (requested >= 5 && reliability < 50) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(com.eventos.banana.R.string.event_detail_ghost),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        
                                        if (requesterProfile.isPerfectAttendee()) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(com.eventos.banana.R.string.event_detail_perfect),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
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
                                        Text(stringResource(com.eventos.banana.R.string.event_detail_reject))
                                    }

                                    Button(
                                        onClick = { onApproveClick(request.userId) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(com.eventos.banana.R.string.event_detail_accept))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
                
                // 🛑 SAFETY SPACER FOR NAVIGATION BARS 🛑
                Spacer(modifier = Modifier.height(80.dp))
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }  // End inner Column (with padding)
            }  // End outer Column (scrollable)
        } else {
            // MURO TAB
            Column(modifier = Modifier.fillMaxSize()) {
                // 🛑 Spacer for Header (Dynamic Height)
                 Spacer(modifier = Modifier.height(headerHeightDp))
                 
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
    } // End Content Box

    // ---------- FLOATING HEADER & TABS (Overlay) ----------
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(headerBackground) // Applied Transparency
            .onGloballyPositioned { coordinates ->
                headerHeightPx = coordinates.size.height.toFloat()
            }
    ) {
        // Header Content
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = event.title, 
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                     // Ensure title is readable on transparent bg
                    color = if (isDetailsTab) Color.White else MaterialTheme.colorScheme.onSurface 
                )
                
                // ACTIONS ROW
                Row {
                    IconButton(onClick = onToggleSave) {
                         Icon(
                            imageVector = if (isSaved) androidx.compose.material.icons.Icons.Filled.Star else androidx.compose.material.icons.Icons.Filled.Add,
                            contentDescription = stringResource(com.eventos.banana.R.string.event_detail_cd_save_event),
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val shareHelper = remember { com.eventos.banana.util.ShareHelper(context) }
                    IconButton(onClick = { shareHelper.shareEvent(event) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(com.eventos.banana.R.string.event_detail_cd_share_event),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Text(
                "${event.region} • ${event.commune}",
                color = if (isDetailsTab) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent, // Tabs container follows header opacity
            contentColor = if (isDetailsTab) Color.White else MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
    }

    // Guide Overlay
    if (showGuide) {
        EventDetailGuideOverlay(onDismiss = {
            showGuide = false
            sharedPreferences.edit().putBoolean("event_detail_guide_seen", true).apply()
        })
    }

    // 🎉 Success Overlay (Join Event)
    var showSuccess by remember { mutableStateOf(false) }
    // Detect successful join (Transition from Joining=true to Joining=false AND (Approved or Pending))
    val wasJoining = remember { mutableStateOf(false) }
    
    LaunchedEffect(isJoining, isApproved, isPending) {
        if (isJoining) {
            wasJoining.value = true
        } else if (wasJoining.value) {
            // Just finished joining
            if (isApproved || isPending) {
                showSuccess = true
            }
            wasJoining.value = false
        }
    }

    if (showSuccess) {
        SuccessOverlay(
            visible = true,
            message = if (event.isPublic) "¡Te has unido!" else "Solicitud enviada",
            onDismiss = { showSuccess = false }
        )
    }

    // Snackbar Host at the bottom
    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        )
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
