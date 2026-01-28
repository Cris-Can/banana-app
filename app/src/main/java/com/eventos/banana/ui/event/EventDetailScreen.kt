package com.eventos.banana.ui.event

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.filled.ArrowForward
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
    onRateParticipants: (Event) -> Unit,
    eventState: com.eventos.banana.domain.model.EventDetailUiState, // Pass full state to access nicknames
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {},
    hasAttended: Boolean = false,
    checkInState: com.eventos.banana.viewmodel.CheckInState = com.eventos.banana.viewmodel.CheckInState.Idle,
    onCheckInClick: () -> Unit = {},
    onResetCheckInState: () -> Unit = {}
) {
    val context = LocalContext.current
    val isCreator = event.creatorId == currentUserId
    val isApproved = event.approvedParticipants.contains(currentUserId)
    val isPending = event.pendingRequests.any { it.userId == currentUserId }
    val isRejected = event.rejectedParticipants.contains(currentUserId)
    val canSeeFeed = isCreator || isApproved

    // Guide State
    val sharedPreferences = remember { context.getSharedPreferences("banana_prefs", android.content.Context.MODE_PRIVATE) }
    var showGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!sharedPreferences.getBoolean("event_detail_guide_seen", false)) {
            showGuide = true
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Detalles", "Muro")

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ---------- HEADER ----------
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
                    modifier = Modifier.weight(1f)
                )
                
                // ACTIONS ROW
                Row {
                    IconButton(onClick = onToggleSave) {
                         Icon(
                            imageVector = if (isSaved) androidx.compose.material.icons.Icons.Filled.Star else androidx.compose.material.icons.Icons.Filled.Add,
                            contentDescription = "Guardar evento",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val shareHelper = remember { com.eventos.banana.util.ShareHelper(context) }
                    IconButton(onClick = { shareHelper.shareEvent(event) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir evento",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
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
                    var showImageDialog by remember { mutableStateOf(false) }

                    Box(
                         modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp) // Taller header
                    ) {
                        AsyncImage(
                            model = event.imageUrl,
                            contentDescription = "Foto del evento",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { showImageDialog = true },
                            contentScale = ContentScale.Crop
                        )
                        
                        // Gradient Overlay for text readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                        startY = 100f
                                    )
                                )
                        )

                        // 🏷️ Category Badge (Top End)
                        Surface(
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.TopEnd),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = "${event.eventType.emoji} ${event.eventType.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

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
                } else {
                     // Placeholder Header if no image
                     Box(
                         modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                         contentAlignment = Alignment.Center
                    ) {
                        Text("🍌", style = MaterialTheme.typography.displayLarge)
                    }
                }

                // Contenido con padding
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title (moved here if image covered it, or redundant)
                    // Let's keep title big here
                    
                    // Date & Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        val dateFormat = java.text.SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale("es", "ES"))
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
                            
                            val creatorNickname = (eventState as? com.eventos.banana.domain.model.EventDetailUiState.Success)
                                    ?.userProfiles?.get(event.creatorId)?.nickname ?: "Cargando..."
                            
                            Text(
                                creatorNickname,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
                                                ?.userProfiles?.get(userId)?.nickname ?: "Usuario"

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
                                            enabled = !isVerifyingLocation && checkInState !is com.eventos.banana.viewmodel.CheckInState.Loading
                                        ) {
                                            if (isVerifyingLocation || checkInState is com.eventos.banana.viewmodel.CheckInState.Loading) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Verificando...")
                                            } else {
                                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Check-in GPS (Estoy aquí)")
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

                // ========== CONFIRMAR ENCUENTROS NFC (REMOVED - DUPLICATE) ==========
                // Logic moved to "Encuentros & Puntuación" section above


                // ========== CALIFICAR PARTICIPANTES (Round 11) ==========
                // Visible para creador y participantes aprobados, solo si el evento terminó
                val now = System.currentTimeMillis()
                val eventEnded = event.endAt < now || event.status == EventStatus.CLOSED
                val canRate = (isCreator || isApproved) && eventEnded
                val ratingDeadline = event.ratingDeadline ?: (event.endAt + (5 * 24 * 60 * 60 * 1000)) // 5 días después
                val withinRatingWindow = now <= ratingDeadline

                if (canRate && withinRatingWindow) {
                    Spacer(Modifier.height(8.dp))
                    
                    // 🔒 VALIDACIÓN DE ASISTENCIA (New Logic)
                    // Si no es creador y no ha asistido (GPS/NFC) -> Bloquear o Advertir
                    if (!isCreator && !hasAttended) {
                         Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("⚠️ Asistencia no verificada", fontWeight = FontWeight.Bold)
                                Text("Para calificar, debiste confirmar tu asistencia con GPS.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "⭐ Calificar Participantes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                
                                val daysRemaining = ((ratingDeadline - now) / (24 * 60 * 60 * 1000)).toInt()
                                Text(
                                    "Tienes $daysRemaining ${if (daysRemaining == 1) "día" else "días"} para calificar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                
                                Spacer(Modifier.height(12.dp))
                                
                                Button(
                                    onClick = { onRateParticipants(event) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Calificar ahora")
                                }
                            }
                        }
                    }
                }

                // SOLICITUDES PENDIENTES
                if (isCreator && event.pendingRequests.isNotEmpty()) {
                    Divider()
                    Text("Solicitudes pendientes", style = MaterialTheme.typography.titleMedium)

                    event.pendingRequests.forEach { request ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {

                                val requesterProfile = (eventState as? com.eventos.banana.domain.model.EventDetailUiState.Success)
                                    ?.userProfiles?.get(request.userId)
                                val requesterName = requesterProfile?.nickname ?: "Usuario"
                                val badge = requesterProfile?.getRatingBadge() ?: ""

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$requesterName $badge",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                // Stats (Punto 5)
                                if (requesterProfile != null) {
                                    val attended = requesterProfile.eventsAttendedCount
                                    val requested = requesterProfile.eventsRequestedCount
                                    val reliability = if (requested > 0) (attended * 100 / requested) else 0
                                    
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "📊 $attended/$requested asignados ($reliability%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        if (requested >= 5 && reliability < 50) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "⚠️ Fantasma",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        
                                        if (requesterProfile.isPerfectAttendee()) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "💎 Perfecto",
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

        // Guide Overlay
        if (showGuide) {
            EventDetailGuideOverlay(onDismiss = {
                showGuide = false
                sharedPreferences.edit().putBoolean("event_detail_guide_seen", true).apply()
            })
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
