package com.eventos.banana.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowDown

import androidx.compose.ui.draw.scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit,
    onFriendsClick: () -> Unit,
    onEventClick: (String) -> Unit = {},
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
    
    // 📸 A23 Fix: Switch to GetContent for maximum compatibility
    // 📸 A23 Fix: Switch to PickVisualMedia for maximum compatibility (Android 13+ & backwards)
    // 📸 MIUI Fix: Use GetContent (SAF) instead of PickVisualMedia to avoid ActivityThread NullPointerException
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null && profile != null) {
                scope.launch {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val uid = sessionViewModel.currentUserId() ?: return@launch
                            profileViewModel.uploadPhoto(uid, bytes, isProfilePicture = pickingAvatar)
                        } else {
                            snackbarHostState.showSnackbar("Error: No se pudo leer la imagen")
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error al procesar imagen: ${e.message}")
                    } finally {
                        pickingAvatar = false
                    }
                }
            } else {
                pickingAvatar = false
            }
        }
    )

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
                title = { Text("Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
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
                    .padding(16.dp),
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

                // 1. HEADER (Avatar + Nickname + Friends)
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // AVATAR
                        Box(contentAlignment = Alignment.BottomEnd) {
                            val avatarModifier = Modifier
                                .size(120.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable { 
                                    pickingAvatar = true
                                    photoPicker.launch("image/*")
                                }

                            if (!profile.profilePictureUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = profile.profilePictureUrl,
                                    contentDescription = "Avatar",
                                    modifier = avatarModifier,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Add,
                                        contentDescription = "Agregar foto",
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // Edit Icon Badge
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                                contentDescription = "Editar",
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                    .padding(6.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        // NICKNAME FIELD
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedTextField(
                                value = nickname,
                                onValueChange = { nickname = it },
                                label = { Text("Nickname") },
                                singleLine = true,
                                trailingIcon = {
                                    if (sessionViewModel.isEmailVerified) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                            contentDescription = "Verificado",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                            
                            if (canSaveNickname) {
                                Button(
                                    onClick = { 
                                        val uid = sessionViewModel.currentUserId() ?: return@Button
                                        profileViewModel.updateNickname(uid, nickname) 
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text("Guardar Nickname")
                                }
                            }
                        }
                    }
                }
                
                // 1.5 REPUTACIÓN BADGE (Round 11)
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            profile.isPerfectAttendee() -> MaterialTheme.colorScheme.tertiaryContainer
                            profile.ratingCount == 0 -> MaterialTheme.colorScheme.surfaceVariant
                            profile.averageRating >= 4.5 -> MaterialTheme.colorScheme.primaryContainer
                            profile.averageRating >= 4.0 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Badge Emoji
                            Text(
                                text = profile.getRatingBadge(),
                                style = MaterialTheme.typography.displaySmall
                            )
                            
                            Column {
                                // Badge Text + Score
                                Text(
                                    text = profile.getRatingBadgeText(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                
                                // Rating count
                                if (profile.ratingCount > 0) {
                                    Text(
                                        text = "⭐ ${String.format("%.1f", profile.averageRating)} (${profile.ratingCount} ${if(profile.ratingCount == 1) "valoración" else "valoraciones"})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "Sin valoraciones aún",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 3. SOCIAL PROFILE
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Perfil Social", style = MaterialTheme.typography.titleMedium)

                        var aboutMe by remember(profile.aboutMe) { mutableStateOf(profile.aboutMe) }
                        OutlinedTextField(
                            value = aboutMe,
                            onValueChange = { aboutMe = it },
                            label = { Text("Sobre mí") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )

                        Text("Intereses", style = MaterialTheme.typography.bodyMedium)
                        var newInterest by remember { mutableStateOf("") }
                        var interests by remember(profile.interests) { mutableStateOf(profile.interests) }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newInterest,
                                onValueChange = { newInterest = it },
                                label = { Text("Nuevo interés") },
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
                        
                         Button(
                            onClick = {
                                val uid = sessionViewModel.currentUserId() ?: return@Button
                                profileViewModel.updateSocialProfile(uid, aboutMe, interests)
                            },
                            enabled = aboutMe != profile.aboutMe || interests != profile.interests,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Actualizar Social")
                        }
                    }
                }

                // 4. PHOTOS (Galeria)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Mis Fotos", style = MaterialTheme.typography.titleMedium)
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
                                                photoPicker.launch("image/*")
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
                                                contentDescription = "Eliminar",
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
                                            contentDescription = "Agregar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4.5 MIS EVENTOS (History & Saved)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Mis Eventos", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        var selectedEventTab by remember { mutableIntStateOf(0) }
                        TabRow(selectedTabIndex = selectedEventTab) {
                            Tab(selected = selectedEventTab == 0, onClick = { selectedEventTab = 0 }, text = { Text("Historial") })
                            Tab(selected = selectedEventTab == 1, onClick = { selectedEventTab = 1 }, text = { Text("Guardados") })
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        val eventsToShow = if (selectedEventTab == 0) historyEvents else savedEvents
                        
                        if (eventsToShow.isEmpty()) {
                            Text(
                                text = if (selectedEventTab == 0) "No tienes eventos recientes" else "No tienes eventos guardados",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                eventsToShow.forEach { event ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth().clickable { onEventClick(event.id) }
                                    ) {
                                        Row(
                                            Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text(
                                                    java.text.SimpleDateFormat("dd MMM", java.util.Locale("es")).format(java.util.Date(event.startAt)), 
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

                // 5. CONFIGURATION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.animateContentSize()) {
                        // Main Header
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { isConfigExpanded = !isConfigExpanded }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚙️ Configuración", style = MaterialTheme.typography.titleMedium)
                            Icon(
                                imageVector = if (isConfigExpanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expandir"
                            )
                        }

                        if (isConfigExpanded) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Divider()
                                Spacer(Modifier.height(16.dp))

                                // --- THEME ---
                                Text("Tema de la App", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val currentTheme = profile.appTheme
                                    listOf("BANANA" to "Banana 🍌", "DARK" to "Dark 🌑", "LIGHT" to "Light ☀️").forEach { (code, label) ->
                                        FilterChip(
                                            selected = currentTheme == code,
                                            onClick = { 
                                                val uid = sessionViewModel.currentUserId() ?: return@FilterChip
                                                profileViewModel.updateAppTheme(uid, code) 
                                            },
                                            label = { Text(label) },
                                            leadingIcon = { if (currentTheme == code) Icon(androidx.compose.material.icons.Icons.Default.Check, null) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // --- PASSWORD ---
                                OutlinedButton(
                                    onClick = { 
                                        val email = profile.email.ifBlank { sessionViewModel.currentUserId() } 
                                        profileViewModel.sendPasswordReset(if (profile.email.isNotBlank()) profile.email else "user@example.com") 
                                        scope.launch { snackbarHostState.showSnackbar("Se enviará un correo para restablecer clave.") }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🔑 Cambiar Contraseña")
                                }
                                
                                Spacer(Modifier.height(16.dp))
                                
                                // --- EMAIL ---
                                if (!sessionViewModel.isEmailVerified) {
                                    Button(
                                        onClick = { sessionViewModel.sendEmailVerification() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("⚠️ Verificar Email")
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }

                                HorizontalDivider()

                                // --- NESTED NOTIFICATIONS ---
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { isNotificationsExpanded = !isNotificationsExpanded }
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🔔 Alertas y Notificaciones", style = MaterialTheme.typography.titleMedium)
                                    Icon(
                                        imageVector = if (isNotificationsExpanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expandir"
                                    )
                                }
                                AnimatedVisibility(
                                    visible = isNotificationsExpanded,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text("Eventos en mi zona", style = MaterialTheme.typography.bodyLarge)
                                        Text("Recibe avisos de eventos en tu comuna", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        // Location Logic
                                        val regionText = detectedRegion ?: profile.region.takeIf { !it.isNullOrBlank() } ?: "Región no definida"
                                        val communeText = detectedCommune ?: profile.commune.takeIf { !it.isNullOrBlank() } ?: "Comuna no definida"
                                        Text("$regionText • $communeText", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        val result = LocationHelper(context).detectLocationFull()
                                                        if (result != null) {
                                                            detectedRegion = result.region
                                                            detectedCommune = result.commune
                                                        } else {
                                                            snackbarHostState.showSnackbar("No se pudo obtener ubicación")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("📍 Detectar") }
                                            Button(
                                                onClick = {
                                                    val uid = sessionViewModel.currentUserId() ?: return@Button
                                                    profileViewModel.updateLocation(uid, detectedRegion!!, detectedCommune!!)
                                                },
                                                enabled = detectedRegion != null && (detectedRegion != profile.region || detectedCommune != profile.commune),
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Guardar") }
                                        }
                                        Switch(
                                            checked = profile.notifyEventsByCommune,
                                            enabled = !profile.commune.isNullOrBlank(),
                                            onCheckedChange = { enabled ->
                                                if (!profile.commune.isNullOrBlank()) {
                                                    val uid = sessionViewModel.currentUserId() ?: return@Switch
                                                    profileViewModel.updateNotifyEventsByCommune(uid, enabled, profile.region, profile.commune)
                                                } else {
                                                    scope.launch { snackbarHostState.showSnackbar("Guarda tu ubicación primero") }
                                                }
                                            },
                                            modifier = Modifier.scale(0.8f).semantics { contentDescription = if (profile.notifyEventsByCommune) "Notificaciones por comuna activadas" else "Notificaciones por comuna desactivadas" }
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        HorizontalDivider()
                                        Spacer(Modifier.height(8.dp))
                                        Text("Categorías de Interés", style = MaterialTheme.typography.bodyLarge)
                                        com.eventos.banana.domain.model.EventType.values().forEach { type ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                            ) {
                                                Text(text = "${type.emoji} ${type.displayName}", style = MaterialTheme.typography.bodyMedium)
                                                val topicName = "events_${type.name}"
                                                val isSubscribed = profile.subscribedCategories.contains(topicName)
                                                Switch(
                                                    checked = isSubscribed,
                                                    onCheckedChange = { isEnabled ->
                                                        val uid = sessionViewModel.currentUserId() ?: return@Switch
                                                        profileViewModel.toggleCategorySubscription(uid, topicName, isEnabled)
                                                    },
                                                    modifier = Modifier.scale(0.8f).semantics { contentDescription = if (isSubscribed) "Suscrito a ${type.displayName}" else "No suscrito a ${type.displayName}" }
                                                )
                                            }
                                        }
                                        // Global notification toggle for highlighted events
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Text(text = "Eventos destacados", style = MaterialTheme.typography.bodyMedium)
                                            val isHighlightedEnabled = profile.notifyEventWall // assume field exists
                                            Switch(
                                                checked = isHighlightedEnabled,
                                                onCheckedChange = { enabled ->
                                                    val uid = sessionViewModel.currentUserId() ?: return@Switch
                                                    profileViewModel.updateNotifyEventWall(uid, enabled)
                                                },
                                                modifier = Modifier.scale(0.8f).semantics { contentDescription = if (isHighlightedEnabled) "Notificaciones de eventos destacados activadas" else "Notificaciones de eventos destacados desactivadas" }
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // LOGOUT BUTTON (at the bottom)
                Spacer(Modifier.height(24.dp))
                
                OutlinedButton(
                    onClick = { sessionViewModel.logout() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("🚪 Cerrar Sesión")
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Instagram Link
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri("https://www.instagram.com/somosbananaapp/") }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📸 Síguenos en Instagram", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
