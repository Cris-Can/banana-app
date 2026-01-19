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
import com.eventos.banana.location.LocationHelper
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowDown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit,
    onFriendsClick: () -> Unit,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val profileUiState by sessionViewModel.profileUiState.collectAsState()
    val uiState by profileViewModel.uiState.collectAsState()
    val profile = profileUiState.profile

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

                        // FRIENDS BUTTON
                        FilledTonalButton(onClick = onFriendsClick, modifier = Modifier.fillMaxWidth()) {
                            Text("👥 Ver Amigos")
                        }
                    }
                }
                
                // 2. CONFIGURATION (Expandable) ⚙️
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.animateContentSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { isConfigExpanded = !isConfigExpanded }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚙️ Configuración", style = MaterialTheme.typography.titleMedium)
                            }
                            Icon(
                                imageVector = if (isConfigExpanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expandir"
                            )
                        }

                        if (isConfigExpanded) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Divider()
                                Spacer(Modifier.height(16.dp))

                                // THEME SELECTOR
                                Text("Tema de la App", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val currentTheme = profile.appTheme
                                    
                                    FilterChip(
                                        selected = currentTheme == "BANANA",
                                        onClick = { 
                                            val uid = sessionViewModel.currentUserId() ?: return@FilterChip
                                            profileViewModel.updateAppTheme(uid, "BANANA") 
                                        },
                                        label = { Text("Banana 🍌") },
                                        leadingIcon = { if (currentTheme == "BANANA") Icon(androidx.compose.material.icons.Icons.Default.Check, null) }
                                    )
                                    FilterChip(
                                        selected = currentTheme == "DARK",
                                        onClick = { 
                                            val uid = sessionViewModel.currentUserId() ?: return@FilterChip
                                            profileViewModel.updateAppTheme(uid, "DARK") 
                                        },
                                        label = { Text("Dark 🌑") },
                                        leadingIcon = { if (currentTheme == "DARK") Icon(androidx.compose.material.icons.Icons.Default.Check, null) }
                                    )
                                    FilterChip(
                                        selected = currentTheme == "LIGHT",
                                        onClick = { 
                                            val uid = sessionViewModel.currentUserId() ?: return@FilterChip
                                            profileViewModel.updateAppTheme(uid, "LIGHT") 
                                        },
                                        label = { Text("Light ☀️") },
                                        leadingIcon = { if (currentTheme == "LIGHT") Icon(androidx.compose.material.icons.Icons.Default.Check, null) }
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                // PASSWORD CHANGE
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
                                
                                // EMAIL VERIFICATION
                                if (!sessionViewModel.isEmailVerified) {
                                    Button(
                                        onClick = { sessionViewModel.sendEmailVerification() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("⚠️ Verificar Email")
                                    }
                                }
                                
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // 3. LOCATION & ALERTS
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Ubicación y Alertas", style = MaterialTheme.typography.titleMedium)
                        
                        val regionText = detectedRegion ?: profile.region.takeIf { !it.isNullOrBlank() } ?: "Región no definida"
                        val communeText = detectedCommune ?: profile.commune.takeIf { !it.isNullOrBlank() } ?: "Comuna no definida"
                        
                        Text("$regionText • $communeText", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val result = LocationHelper(context).getRegionAndCommune()
                                        if (result.region != null && result.commune != null) {
                                            detectedRegion = result.region
                                            detectedCommune = result.commune
                                        } else {
                                            snackbarHostState.showSnackbar("No se pudo obtener ubicación")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📍 Detectar")
                            }

                            Button(
                                onClick = {
                                    val uid = sessionViewModel.currentUserId() ?: return@Button
                                    profileViewModel.updateLocation(uid, detectedRegion!!, detectedCommune!!)
                                },
                                enabled = detectedRegion != null && (detectedRegion != profile.region || detectedCommune != profile.commune),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Guardar")
                            }
                        }

                        Divider()
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("🔔 Alertas en mi comuna", style = MaterialTheme.typography.bodyLarge)
                                Text("Recibe avisos de nuevos eventos", style = MaterialTheme.typography.bodySmall)
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
                                }
                            )
                        }
                    }
                }

                // 4. SOCIAL PROFILE
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

                // 5. PHOTOS (Galeria)
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
            }
        }
    }
}
