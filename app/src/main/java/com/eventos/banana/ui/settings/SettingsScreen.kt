package com.eventos.banana.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToGold: () -> Unit,
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
    onRecalculateStats: (String) -> Unit,
    onUpdateLocation: (String, String, String) -> Unit,
    onUpdateNotifyCommune: (Boolean, String, String) -> Unit,
    onToggleCategorySubscription: (String, Boolean) -> Unit,
    onUpdateNotifyWall: (Boolean) -> Unit,
    onNavigateToIcons: () -> Unit,
    onMigrateEvents: () -> Unit, // New
    migrationStatus: String?,    // New
    profileUiState: com.eventos.banana.viewmodel.ProfileUiState
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("banana_prefs", Context.MODE_PRIVATE) }

    // State for Toggles
    var notifyWall by remember { mutableStateOf(prefs.getBoolean("pref_notify_wall", true)) }
    var notifyMessages by remember { mutableStateOf(prefs.getBoolean("pref_notify_msg", true)) }
    var notifyGeneral by remember { mutableStateOf(prefs.getBoolean("pref_notify_general", true)) }

    // Save changes
    fun savePref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        // Here you could also sync with server if needed
    }

    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes de Notificaciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    if (userProfile != null) {
                        onRecalculateStats(userProfile.uid)
                        onMigrateEvents()
                    }
                    kotlinx.coroutines.delay(2000)
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Hint Text
                Box(Modifier.fillMaxWidth(), androidx.compose.ui.Alignment.Center) {
                    Text(
                        "👇 Desliza para actualizar datos y migrar eventos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Text(
                    "Configura qué notificaciones quieres recibir en tu dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 💎 GOLD BANNER
                Card(
                    onClick = onNavigateToGold,
                    colors = CardDefaults.cardColors(containerColor = com.eventos.banana.ui.theme.BananaGold),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("🍌", fontSize = 32.sp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "Banana Gold", 
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.Black
                                )
                            )
                            Text(
                                "Gestiona tu suscripción",
                                style = MaterialTheme.typography.bodySmall.copy(color = androidx.compose.ui.graphics.Color.Black)
                            )
                        }
                    }
                }
    
                HorizontalDivider()
    
                // 🍌 ICONO DE APP (GOLD)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Icono de App", style = MaterialTheme.typography.labelLarge)
                        Text("Personaliza el icono en tu inicio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Button(onClick = onNavigateToIcons) {
                        Text("Cambiar")
                    }
                }
    
                HorizontalDivider()
    
                // 🎨 TEMA
                Text("Tema de la App", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentTheme = userProfile?.appTheme ?: "BANANA"
                    val themes = mutableListOf("BANANA" to "Banana 🍌", "DARK" to "Dark 🌑", "LIGHT" to "Light ☀️")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) themes.add("DYNAMIC" to "Dynamic 🎨")
    
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(themes.size) { index ->
                            val (code, label) = themes[index]
                            FilterChip(
                                selected = currentTheme == code,
                                onClick = { onUpdateTheme(code) },
                                label = { Text(label) },
                                leadingIcon = { if (currentTheme == code) Icon(androidx.compose.material.icons.Icons.Default.Check, null) }
                            )
                        }
                    }
                }
    
                HorizontalDivider()
    
                // 🔔 NOTIFICACIONES
                Text("Alertas y Ubicación", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                // Wall Posts
                SettingsSwitch(
                    title = "Publicaciones en Muro",
                    description = "Avisar cuando comentan en eventos.",
                    checked = notifyWall,
                    onCheckedChange = {
                        notifyWall = it
                        savePref("pref_notify_wall", it)
                        onUpdateNotifyWall(it)
                    }
                )
    
                // Location Logic
                var detectedRegion by remember { mutableStateOf<String?>(null) }
                var detectedCommune by remember { mutableStateOf<String?>(null) }
                val coroutineScope = rememberCoroutineScope()
                
                Column {
                    Text("Ubicación para Alertas", style = MaterialTheme.typography.titleSmall)
                    Text("Recibe avisos de eventos en tu comuna", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    val regionText = detectedRegion ?: userProfile?.region.takeIf { !it.isNullOrBlank() } ?: "Región no definida"
                    val communeText = detectedCommune ?: userProfile?.commune.takeIf { !it.isNullOrBlank() } ?: "Comuna no definida"
                    
                    Text("$regionText • $communeText", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        com.eventos.banana.ui.components.BananaOutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val result = com.eventos.banana.util.LocationHelper(context).detectLocationFull()
                                    if (result != null) {
                                        detectedRegion = result.region
                                        detectedCommune = result.commune
                                    }
                                }
                            },
                            text = "📍 Detectar",
                            modifier = Modifier.weight(1f)
                        )
                        com.eventos.banana.ui.components.BananaButton(
                            onClick = {
                                if (userProfile != null && detectedRegion != null && detectedCommune != null) {
                                    onUpdateLocation(userProfile.uid, detectedRegion!!, detectedCommune!!)
                                }
                            },
                            text = "Guardar",
                            enabled = detectedRegion != null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Notify Events Switch
                    val startChecked = userProfile?.notifyEventsByCommune == true
                    SettingsSwitch(
                        title = "Eventos en mi zona",
                        description = "Avisar cuando hay nuevos eventos en mi comuna.",
                        checked = startChecked,
                        onCheckedChange = { enabled ->
                            if (userProfile?.commune != null) {
                                 onUpdateNotifyCommune(enabled, userProfile.region ?: "", userProfile.commune)
                            }
                        }
                    )
                }
                
                HorizontalDivider()
                
                // 🏷️ CATEGORÍAS
                Text("Categorías de Interés", style = MaterialTheme.typography.titleSmall)
                com.eventos.banana.domain.model.EventType.values().forEach { type ->
                    val topicName = "events_${type.name}"
                    val isSubscribed = userProfile?.subscribedCategories?.contains(topicName) == true
                    SettingsSwitch(
                        title = "${type.emoji} ${type.displayName}",
                        description = "",
                        checked = isSubscribed,
                        onCheckedChange = { isEnabled ->
                            onToggleCategorySubscription(topicName, isEnabled)
                        }
                    )
                }
                
                HorizontalDivider()
    
                // 🔐 SEGURIDAD
                Text("Seguridad", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                // Password
                com.eventos.banana.ui.components.BananaOutlinedButton(
                    onClick = { 
                        val mail = userProfile?.email.takeIf { !it.isNullOrBlank() } ?: "user@example.com"
                        onSendPasswordReset(mail)
                    },
                    text = "🔑 Cambiar Contraseña",
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Email Verification
                if (!isEmailVerified) {
                    Spacer(Modifier.height(8.dp))
                    com.eventos.banana.ui.components.BananaButton(
                        onClick = onVerifyEmail,
                        text = "⚠️ Verificar Email",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
    
     
                HorizontalDivider()
                
                Text(
                    "Cuenta",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
    
                // Tutorial
                OutlinedButton(
                    onClick = onGuideReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📖 Ver Tutorial de Bienvenida")
                }
                
                // Logout
                com.eventos.banana.ui.components.BananaOutlinedButton(
                    onClick = onLogout,
                    text = "🚪 Cerrar Sesión",
                    contentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
    
                // Logic for showing migration status via text is kept, but button removed
                if (migrationStatus != null) {
                    Text(
                        text = migrationStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (migrationStatus.contains("Error") || migrationStatus.contains("❌")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                
                // Delete Account Logic
                var showDeleteConfirm by remember { mutableStateOf(false) }

                LaunchedEffect(deleteAccountStatus) {
                    if (deleteAccountStatus != null && deleteAccountStatus != "LOADING") {
                        // If success or error, we might want to show it or auto-dismiss
                        // For now we assume the ViewModel handles the actual logout if success
                       if (deleteAccountStatus == "SUCCESS") {
                           onLogout()
                       }
                        onResetDeleteStatus()
                        showDeleteConfirm = false 
                    }
                }
                
                if (showDeleteConfirm) {
                     AlertDialog(
                        onDismissRequest = { if (deleteAccountStatus != "LOADING") showDeleteConfirm = false },
                        title = { Text(if (deleteAccountStatus == "LOADING") "Eliminando..." else "¿Eliminar cuenta?") },
                        text = { 
                            if (deleteAccountStatus == "LOADING") {
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    CircularProgressIndicator()
                                    Spacer(Modifier.height(8.dp))
                                    Text("Borrando tu rastro de la matrix...")
                                }
                            } else {
                                Text("Esta acción es irreversible. Perderás todo tu historial y rango.")
                            }
                        },
                        confirmButton = {
                            if (deleteAccountStatus != "LOADING") {
                                TextButton(
                                    onClick = onDeleteAccount,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Eliminar definitivamente")
                                }
                            }
                        },
                        dismissButton = {
                            if (deleteAccountStatus != "LOADING") {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text("Cancelar")
                                }
                            }
                        }
                    )
                }
                
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🗑️ Eliminar mi cuenta", color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        // verticalAlignment = Alignment.CenterVertically // Default is top which is better for long text
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
