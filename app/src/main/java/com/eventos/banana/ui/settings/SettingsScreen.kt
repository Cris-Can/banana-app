package com.eventos.banana.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eventos.banana.ui.util.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToGold: () -> Unit,
    onNavigateToAdmin: () -> Unit, // 👮
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    deleteAccountStatus: String?, 
    onResetDeleteStatus: () -> Unit,
    onGuideReset: () -> Unit,
    // New Params
    userProfile: com.eventos.banana.domain.model.UserProfile?,
    onUpdateTheme: (String) -> Unit,
    onSendPasswordReset: (String) -> Unit,
    onVerifyEmail: () -> Unit,
    isEmailVerified: Boolean,
    onUpdateLocation: (String, String, String, String, Double?, Double?) -> Unit,
    onUpdateNotifyCommune: (Boolean, String, String) -> Unit,
    onToggleCategorySubscription: (String, Boolean) -> Unit,
    onUpdateNotifyWall: (Boolean) -> Unit,
    onNavigateToIcons: () -> Unit,
    onNavigateToBlockedUsers: () -> Unit, // 🛡️ Blocked Users
    profileUiState: com.eventos.banana.ui.profile.ProfileUiState,
    locationMessage: String?,
    onClearLocationMessage: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("banana_prefs", Context.MODE_PRIVATE) }

    // State for Toggles
    var notifyWall by remember { mutableStateOf(prefs.getBoolean("pref_notify_wall", true)) }

    // Save changes
    fun savePref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // Logic for Location
    var detectedRegion by remember { mutableStateOf<String?>(null) }
    var detectedCommune by remember { mutableStateOf<String?>(null) }
    var detectedCountry by remember { mutableStateOf<String?>(null) }
    var isDetecting by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // 📍 Location Success Feedback
    LaunchedEffect(locationMessage) {
        if (!locationMessage.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(locationMessage)
            onClearLocationMessage()
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.eventos.banana.R.string.settings_notifications_title), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.eventos.banana.R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    kotlinx.coroutines.delay(1000)
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                
                // 💎 BANANA GOLD SECTION
                Card(
                    onClick = onNavigateToGold,
                    colors = CardDefaults.cardColors(containerColor = com.eventos.banana.ui.theme.BananaGold),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("👑", fontSize = 32.sp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                stringResource(com.eventos.banana.R.string.settings_banana_gold), 
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.Black
                                )
                            )
                            Text(
                                stringResource(com.eventos.banana.R.string.settings_manage_subscription),
                                style = MaterialTheme.typography.bodySmall.copy(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f))
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = androidx.compose.ui.graphics.Color.Black)
                    }
                }

                // 👤 CUENTA Y PERSONALIZACIÓN
                SettingsSection(title = "Cuenta y Personalización") {
                    SettingsItem(
                        icon = Icons.Outlined.Create, // App Icon
                        title = stringResource(com.eventos.banana.R.string.settings_app_icon),
                        subtitle = stringResource(com.eventos.banana.R.string.settings_app_icon_desc),
                        onClick = onNavigateToIcons
                    )
                    
                    // Theme Selector embedded
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            Icon(Icons.Outlined.Face, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(com.eventos.banana.R.string.settings_theme_title), style = MaterialTheme.typography.bodyLarge)
                        }
                        
                        Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                             val currentTheme = userProfile?.appTheme ?: "BANANA"
                             val themes = listOf(
                                Triple("BANANA", "Banana", androidx.compose.ui.graphics.Color(0xFFFFD700)),
                                Triple("DARK", "Dark", androidx.compose.ui.graphics.Color(0xFF212121)),
                                Triple("LIGHT", "Light", androidx.compose.ui.graphics.Color(0xFFEEEEEE))
                             )
                             
                             themes.forEach { (code, label, color) ->
                                 FilterChip(
                                     selected = currentTheme == code,
                                     onClick = { onUpdateTheme(code) },
                                     label = { Text(label) },
                                     leadingIcon = {
                                         androidx.compose.foundation.layout.Box(
                                             modifier = Modifier.size(12.dp).background(color, androidx.compose.foundation.shape.CircleShape)
                                         )
                                     }
                                 )
                             }
                        }
                    }
                }

                // 📍 UBICACIÓN
                SettingsSection(title = "Ubicación") {
                    var showPlaceSearchDialog by remember { mutableStateOf(false) }
                    var isFetchingCoordinates by remember { mutableStateOf(false) }
                    val placesClient = remember { com.eventos.banana.util.LocationHelper(context).getPlacesClient() }

                    val regionText = detectedRegion ?: userProfile?.region.takeIf { !it.isNullOrBlank() } ?: "Sin región"
                    val communeText = detectedCommune ?: userProfile?.commune.takeIf { !it.isNullOrBlank() } ?: "Sin comuna"
                    val countryText = detectedCountry ?: userProfile?.country.takeIf { !it.isNullOrBlank() } ?: ""
                    
                    val locationTitle = if (countryText.isNotBlank()) "$communeText, $regionText, $countryText" else "$communeText, $regionText"

                    Column(modifier = Modifier.animateContentSize()) {
                        SettingsItem(
                            icon = Icons.Outlined.LocationOn,
                            title = when {
                                isDetecting -> "Detectando ubicación..."
                                isFetchingCoordinates -> "Buscando coordenadas..."
                                else -> locationTitle
                            },
                            subtitle = if (isDetecting || isFetchingCoordinates) "Por favor espera..." else "Toca para cambiar tu ciudad",
                            onClick = {
                                if (!isDetecting && !isFetchingCoordinates) {
                                    showPlaceSearchDialog = true
                                }
                            },
                            trailingContent = {
                                if (isDetecting || isFetchingCoordinates) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                            }
                        )
                        
                        // Botón secundario para detección automática rápida
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isDetecting && !isFetchingCoordinates,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isDetecting = true
                                        val result = com.eventos.banana.util.LocationHelper(context).detectLocationFull()
                                        if (result != null) {
                                            detectedRegion = result.region
                                            detectedCommune = result.commune
                                            detectedCountry = result.country
                                            if (userProfile != null) {
                                                 onUpdateLocation(
                                                    userProfile.uid, 
                                                    result.region, 
                                                    result.commune, 
                                                    result.country,
                                                    result.latitude, 
                                                    result.longitude
                                                )
                                            }
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            android.widget.Toast.makeText(context, "No se pudo detectar la ubicación.", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                        isDetecting = false
                                    }
                                },
                                modifier = Modifier.padding(start = 56.dp)
                            ) {
                                Text("📍 Usar ubicación actual", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (showPlaceSearchDialog) {
                        com.eventos.banana.ui.components.GooglePlacesSearchDialog(
                            onDismiss = { showPlaceSearchDialog = false },
                            onPlaceSelected = { placeId, fullText ->
                                showPlaceSearchDialog = false
                                isFetchingCoordinates = true
                                
                                // 1. Update text locally
                                val parts = fullText.split(",")
                                detectedCommune = parts.firstOrNull()?.trim() ?: fullText
                                detectedRegion = if (parts.size > 1) parts[1].trim() else ""
                                detectedCountry = if (parts.size > 2) parts.last().trim() else detectedRegion
                                
                                // 2. Fetch Coordinates
                                val fields = listOf(com.google.android.libraries.places.api.model.Place.Field.LAT_LNG)
                                val request = com.google.android.libraries.places.api.net.FetchPlaceRequest.builder(placeId, fields).build()
                                
                                placesClient.fetchPlace(request).addOnSuccessListener { response ->
                                    val place = response.place
                                    if (userProfile != null) {
                                        onUpdateLocation(
                                            userProfile.uid,
                                            detectedRegion ?: "",
                                            detectedCommune ?: "",
                                            detectedCountry ?: "",
                                            place.latLng?.latitude,
                                            place.latLng?.longitude
                                        )
                                    }
                                    isFetchingCoordinates = false
                                }.addOnFailureListener {
                                    isFetchingCoordinates = false
                                    android.widget.Toast.makeText(context, "Error al obtener coordenadas", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    
                     SettingsSwitchItem(
                        icon = Icons.Outlined.Place,
                        title = stringResource(com.eventos.banana.R.string.settings_events_nearby),
                        subtitle = stringResource(com.eventos.banana.R.string.settings_events_nearby_desc),
                        checked = userProfile?.notifyEventsByCommune == true,
                        onCheckedChange = { enabled ->
                             if (userProfile?.commune != null) {
                                 onUpdateNotifyCommune(enabled, userProfile.region ?: "", userProfile.commune)
                             }
                        }
                    )
                }

                // 🔔 NOTIFICACIONES
                SettingsSection(title = stringResource(com.eventos.banana.R.string.settings_notifications_title)) {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(com.eventos.banana.R.string.settings_wall_posts),
                        subtitle = stringResource(com.eventos.banana.R.string.settings_wall_posts_desc),
                        checked = notifyWall,
                        onCheckedChange = {
                            notifyWall = it
                            savePref("pref_notify_wall", it)
                            onUpdateNotifyWall(it)
                        }
                    )
                    
                    // 🔔 Push Connectivity Indicator
                    val hasFcmToken = !userProfile?.fcmToken.isNullOrBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (hasFcmToken) androidx.compose.ui.graphics.Color(0xFF4CAF50) 
                                            else androidx.compose.ui.graphics.Color(0xFFF44336),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (hasFcmToken) "Push activo" else "Push desconectado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 🏷️ INTERESES
                SettingsSection(title = stringResource(com.eventos.banana.R.string.settings_interest_categories)) {
                    com.eventos.banana.domain.model.EventType.values().forEach { type ->
                        val topicName = "events_${type.name}"
                        val isSubscribed = userProfile?.subscribedCategories?.contains(topicName) == true
                         SettingsSwitchItem(
                            icon = Icons.Outlined.FavoriteBorder, // Generic icon for all, or map specific ones
                            title = "${type.emoji} ${type.localizedName()}",
                            subtitle = null,
                            checked = isSubscribed,
                            onCheckedChange = { isEnabled ->
                                onToggleCategorySubscription(topicName, isEnabled)
                            }
                        )
                    }
                }

                // 🛡️ SEGURIDAD
                SettingsSection(title = stringResource(com.eventos.banana.R.string.settings_security)) {
                     SettingsItem(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(com.eventos.banana.R.string.settings_change_password),
                        onClick = { 
                            val mail = userProfile?.email.takeIf { !it.isNullOrBlank() } ?: "user@example.com"
                            onSendPasswordReset(mail)
                        }
                    )
                    
                    if (!isEmailVerified) {
                         SettingsItem(
                            icon = Icons.Outlined.Email,
                            title = stringResource(com.eventos.banana.R.string.settings_verify_email),
                            subtitle = "Tu correo no está verificado",
                            onClick = onVerifyEmail,
                            textColor = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // 🛡️ Usuarios Bloqueados
                    SettingsItem(
                        icon = Icons.Outlined.Lock,
                        title = "Usuarios Bloqueados",
                        subtitle = "Gestionar tu lista de bloqueados",
                        onClick = onNavigateToBlockedUsers
                    )
                }

                // 🎟️ CANJEAR CÓDIGO DE INVITACIÓN (Visible para usuarios NO Founder)
                if (userProfile?.subscriptionType != "FOUNDER") {
                    SettingsSection(title = "Código de Invitación") {
                        var redeemCode by remember { mutableStateOf("") }
                        var isRedeeming by remember { mutableStateOf(false) }
                        var redeemResult by remember { mutableStateOf<String?>(null) }
                        var redeemIsError by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "¿Tienes un código exclusivo?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = redeemCode,
                                onValueChange = { 
                                    redeemCode = it.uppercase().trim()
                                    redeemResult = null // Limpiar resultado anterior
                                },
                                label = { Text("Código de Invitación") },
                                placeholder = { Text("Ej: FOUNDER-XXXX") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isRedeeming,
                                shape = RoundedCornerShape(12.dp)
                            )

                            AnimatedVisibility(
                                visible = redeemResult != null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Text(
                                    text = redeemResult ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (redeemIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }

                            Button(
                                onClick = {
                                    if (redeemCode.isBlank()) return@Button
                                    isRedeeming = true
                                    redeemResult = null
                                    scope.launch {
                                        try {
                                            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
                                            val data = hashMapOf("code" to redeemCode)
                                            val result = functions.getHttpsCallable("redeemFounderCode").call(data).await()
                                            @Suppress("UNCHECKED_CAST")
                                            val response = result.data as? Map<String, Any>
                                            redeemResult = response?.get("message")?.toString() ?: "¡Código canjeado exitosamente!"
                                            redeemIsError = false
                                            redeemCode = ""
                                        } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
                                            redeemResult = e.message ?: "Error al canjear el código."
                                            redeemIsError = true
                                        } catch (e: Exception) {
                                            redeemResult = "Error de conexión. Inténtalo de nuevo."
                                            redeemIsError = true
                                        } finally {
                                            isRedeeming = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = redeemCode.isNotBlank() && !isRedeeming,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isRedeeming) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Verificando...")
                                } else {
                                    Text("🎟️ Canjear Código")
                                }
                            }
                        }
                    }
                }
                
                // 👮 ADMIN (Visible only for admins)
                if (userProfile?.admin == true) {
                    SettingsSection(title = "Panel de Administración") {
                        SettingsItem(
                            icon = Icons.Filled.Warning,
                            title = "Gestionar Reportes & Usuarios",
                            subtitle = "Zona restringida para admins",
                            onClick = onNavigateToAdmin,
                            textColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // ⚠️ ZONA DE PELIGRO / OTROS
                SettingsSection(title = "Otros") {
                     SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = stringResource(com.eventos.banana.R.string.settings_guide_reset),
                        onClick = onGuideReset
                    )
                    
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        title = stringResource(com.eventos.banana.R.string.settings_logout),
                        onClick = onLogout,
                        textColor = MaterialTheme.colorScheme.error
                    )
                    
                    // Delete Account
                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    
                     SettingsItem(
                        icon = Icons.Outlined.Delete,
                        title = stringResource(com.eventos.banana.R.string.settings_delete_account),
                        onClick = { showDeleteConfirm = true },
                        textColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f) // Subtle
                    )

                    // DELETE CONFIRMATION DIALOG (Logic Kept)
                    if (showDeleteConfirm) {
                         AlertDialog(
                            onDismissRequest = { if (deleteAccountStatus != "LOADING") showDeleteConfirm = false },
                            title = { Text(if (deleteAccountStatus == "LOADING") stringResource(com.eventos.banana.R.string.settings_delete_loading) else stringResource(com.eventos.banana.R.string.settings_delete_confirm_title)) },
                            text = { 
                                if (deleteAccountStatus == "LOADING") {
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(8.dp))
                                        Text(stringResource(com.eventos.banana.R.string.settings_delete_loading_msg))
                                    }
                                } else {
                                    Text(stringResource(com.eventos.banana.R.string.settings_delete_confirm_body))
                                }
                            },
                            confirmButton = {
                                if (deleteAccountStatus != "LOADING") {
                                    TextButton(
                                        onClick = onDeleteAccount,
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text(stringResource(com.eventos.banana.R.string.settings_delete_permanently))
                                    }
                                }
                            },
                            dismissButton = {
                                if (deleteAccountStatus != "LOADING") {
                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text(stringResource(com.eventos.banana.R.string.common_cancel))
                                    }
                                }
                            }
                        )
                    }
                    
                    // Auto-Dismiss Delete Logic
                    LaunchedEffect(deleteAccountStatus) {
                        if (deleteAccountStatus != null && deleteAccountStatus != "LOADING") {
                           if (deleteAccountStatus == "SUCCESS") onLogout()
                           onResetDeleteStatus()
                           showDeleteConfirm = false 
                        }
                    }
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// 📐 HELPER COMPOSABLES (Frontend Bonito)

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat styling
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = textColor.takeIf { it != MaterialTheme.colorScheme.onSurface } ?: MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = textColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        if (trailingContent != null) {
            trailingContent()
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
