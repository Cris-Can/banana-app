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
    onBoostWithCredit: () -> Unit = {},
    credits: Int = 0,
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
    joinSubmissionState: com.eventos.banana.ui.event.JoinSubmissionState = com.eventos.banana.ui.event.JoinSubmissionState.Idle,
    onResetJoinSubmissionState: () -> Unit = {},
    adUnlockState: com.eventos.banana.ui.event.EventDetailViewModel.UnlockState = com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Idle,
    onWatchAd: () -> Unit = {},
    onResetAdUnlockState: () -> Unit = {},
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
                    // ========== FOTO DEL EVENTO ==========
                    com.eventos.banana.ui.event.components.EventDetailImageArea(
                        imageUrl = event.imageUrl,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )

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

                    // Creator Card & Boost Settings
                    com.eventos.banana.ui.event.components.EventDetailCreatorCard(
                        event = event,
                        eventState = eventState,
                        currentUserId = currentUserId,
                        isCreator = isCreator,
                        onUserClick = onUserClick,
                        onBoostClick = onBoostClick,
                        onBoostWithCredit = onBoostWithCredit,
                        credits = credits,
                        modifier = Modifier.fillMaxWidth()
                    )


                    // ========== PARTICIPANTES ==========
                    com.eventos.banana.ui.event.components.EventDetailParticipantsCard(
                        event = event,
                        eventState = eventState,
                        isCreator = isCreator,
                        isApproved = isApproved,
                        onUserClick = onUserClick,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ========== ENCUENTROS Y PUNTUACIÓN ==========
                    com.eventos.banana.ui.event.components.EventDetailCheckInCard(
                        event = event,
                        hasAttended = hasAttended,
                        isCreator = isCreator,
                        isApproved = isApproved,
                        checkInState = checkInState,
                        onCheckInClick = onCheckInClick,
                        onRateParticipants = onRateParticipants,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ========== UBICACIÓN EN MAPA ==========
                    com.eventos.banana.ui.event.components.EventDetailMapCard(
                        event = event,
                        isCreator = isCreator,
                        isApproved = isApproved
                    )

                // ---------- ESTADO ----------
                when (event.status) {
                    EventStatus.CANCELLED -> {
                        Text(
                            "❌ Evento cancelado",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
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

                // ========== ACCIONES FINALES ==========
                com.eventos.banana.ui.event.components.EventDetailActionButtons(
                    event = event,
                    isCreator = isCreator,
                    isApproved = isApproved,
                    isPending = isPending,
                    isRejected = isRejected,
                    isJoining = isJoining,
                    onJoinClick = onJoinClick,
                    onCloseEvent = onCloseEvent,
                    onCancelEvent = onCancelEvent,
                    modifier = Modifier.fillMaxWidth()
                )

                // ========== CONFIRMAR ENCUENTROS NFC (REMOVED - DUPLICATE) ==========
                // Logic moved to "Encuentros & Puntuación" section above


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

                    }
                }
                
                // 🛑 SAFETY SPACER FOR NAVIGATION BARS 🛑
                Spacer(modifier = Modifier.height(80.dp))
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
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
    }


    // ---------- FLOATING HEADER & TABS (Overlay) ----------
    com.eventos.banana.ui.event.components.EventDetailHeader(
        event = event,
        isDetailsTab = isDetailsTab,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        isSaved = isSaved,
        onToggleSave = onToggleSave,
        headerBackground = headerBackground,
        onHeaderHeightMeasured = { height -> headerHeightPx = height },
        modifier = Modifier.align(Alignment.TopCenter)
    )

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
} // End Outer Box (includes overlays)

    // ========== 📺 AD UNLOCK DIALOG (JOIN) ==========
    var showAdDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(joinSubmissionState) {
        if (joinSubmissionState is com.eventos.banana.ui.event.JoinSubmissionState.Error && 
            (joinSubmissionState as com.eventos.banana.ui.event.JoinSubmissionState.Error).message == "LIMIT_REACHED") {
            showAdDialog = true
        }
    }

    if (showAdDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (adUnlockState !is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.LoadingAd) {
                    showAdDialog = false 
                    onResetJoinSubmissionState()
                    onResetAdUnlockState()
                }
            },
            title = {
                when (adUnlockState) {
                    is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Unlocked -> Text("¡Desbloqueado! 🎉")
                    else -> Text("Límite de Solicitudes")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val state = adUnlockState) {
                        is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Idle -> {
                            Text("Has alcanzado tu límite gratuito. Puedes ver 2 anuncios para desbloquear 1 solicitud extra.")
                        }
                        is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.LoadingAd -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Cargando anuncio...")
                            }
                        }
                        is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Progress -> {
                            LinearProgressIndicator(
                                progress = { state.watched.toFloat() / state.required.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                            Text("Has visto ${state.watched} de ${state.required} anuncios. ¡Falta poco!")
                        }
                        is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Unlocked -> {
                            Text("¡Ya tienes un cupo extra! Presiona Unirse otra vez.")
                        }
                        is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Error -> {
                            Text("Hubo un problema: ${state.message}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                when (val state = adUnlockState) {
                    is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Unlocked -> {
                        Button(onClick = { 
                            showAdDialog = false 
                            onResetJoinSubmissionState()
                            onResetAdUnlockState()
                        }) {
                            Text("Continuar")
                        }
                    }
                    is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.LoadingAd -> { /* Loading */ }
                    else -> {
                        Button(onClick = onWatchAd) {
                            Text(if (state is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Progress) "Ver Siguiente" else "Ver Anuncio")
                        }
                    }
                }
            },
            dismissButton = {
                if (adUnlockState !is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.LoadingAd && adUnlockState !is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Unlocked) {
                    TextButton(onClick = { 
                        showAdDialog = false 
                        onResetJoinSubmissionState()
                        onResetAdUnlockState()
                    }) {
                        Text("Cancelar")
                    }
                }
            }
        )
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
