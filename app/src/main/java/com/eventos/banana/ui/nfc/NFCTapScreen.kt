package com.eventos.banana.ui.nfc

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.util.NFCManager
import com.eventos.banana.viewmodel.NFCEncounterViewModel
import com.eventos.banana.ui.components.ConfettiEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NFCTapScreen(
    eventId: String,
    currentUserId: String,
    participantIds: List<String>,
    onBackClick: () -> Unit
) {
    val viewModel: NFCEncounterViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return NFCEncounterViewModel(eventId, currentUserId, participantIds) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // NFC Manager
    val nfcManager = remember(activity) {
        activity?.let { NFCManager(it) }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Check NFC status on init and RESUME
    DisposableEffect(lifecycleOwner, nfcManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
             if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                 if (nfcManager != null) {
                    viewModel.setNfcStatus(
                        available = nfcManager.isNfcAvailable(),
                        enabled = nfcManager.isNfcEnabled()
                    )
                } else {
                     viewModel.setNfcStatus(available = false, enabled = false)
                }
             }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle NFC reader mode
    DisposableEffect(uiState.isNfcActive) {
        if (uiState.isNfcActive && nfcManager != null) {
            nfcManager.enableReaderMode { detectedUserId ->
                viewModel.recordNFCEncounter(detectedUserId)
            }
        }

        onDispose {
            nfcManager?.disableReaderMode()
        }
    }

    // State for Confetti
    var showConfetti by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("📱 Confirmar Encuentros") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // NFC Status Card
                NFCStatusCard(
                    isNfcAvailable = uiState.nfcAvailable,
                    isNfcEnabled = uiState.nfcEnabled,
                    isNfcActive = uiState.isNfcActive,
                    onToggleNfc = { active ->
                        viewModel.setNfcActive(active)
                    }
                )

                Spacer(Modifier.height(16.dp))

                // Progress
                val total = uiState.participants.size
                val confirmed = uiState.confirmedEncounters.size
                
                Text(
                    "Confirmados: $confirmed/$total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { confirmed.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Participants List
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.participants) { participant ->
                            ParticipantNFCCard(
                                participant = participant,
                                isConfirmed = participant.uid in uiState.confirmedEncounters,
                                onDebugSimulate = {
                                    // 🐛 DEBUG: Simular encuentro
                                    viewModel.simulateNFCEncounter(participant.uid)
                                }
                            )
                        }
                    }
                }

                // Messages
                uiState.errorMessage?.let { error ->
                    Snackbar(
                        modifier = Modifier.padding(8.dp),
                        action = {
                            TextButton(onClick = { viewModel.clearMessages() }) {
                                Text("OK")
                            }
                        }
                    ) {
                        Text(error)
                    }
                }

                uiState.successMessage?.let { success ->
                    LaunchedEffect(success) {
                        showConfetti = true

                        // Haptic Feedback / Vibration
                        try {
                            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                val vibratorManager = context.getSystemService(android.os.VibratorManager::class.java)
                                vibratorManager?.defaultVibrator
                            } else {
                                @Suppress("DEPRECATION")
                                context.getSystemService(android.os.Vibrator::class.java)
                            }

                            if (vibrator?.hasVibrator() == true) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator.vibrate(
                                        android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(150)
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore vibration errors
                        }

                        kotlinx.coroutines.delay(2000)
                        viewModel.clearMessages()
                    }
                }
            }
        }
        
        // 🎉 Confetti Overlay
        if (showConfetti) {
            ConfettiEffect(
                onComplete = { showConfetti = false }
            )
        }
    }
}

@Composable
fun NFCStatusCard(
    isNfcAvailable: Boolean,
    isNfcEnabled: Boolean,
    isNfcActive: Boolean,
    onToggleNfc: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !isNfcAvailable -> MaterialTheme.colorScheme.errorContainer
                !isNfcEnabled -> MaterialTheme.colorScheme.errorContainer
                isNfcActive -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon replaced with emoji
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "📱",
                    style = MaterialTheme.typography.displaySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            when {
                !isNfcAvailable -> {
                    Text(
                        "❌ NFC no disponible",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Este dispositivo no tiene NFC",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                !isNfcEnabled -> {
                    Text(
                        "⚠️ NFC desactivado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Activa NFC en la configuración",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
                                androidx.core.content.ContextCompat.startActivity(context, intent, null)
                            } catch (e: Exception) {
                                // Fallback for some devices
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                                    androidx.core.content.ContextCompat.startActivity(context, intent, null) 
                                } catch (e2: Exception) {}
                            }
                        }
                    ) {
                        Text("Ir a Configuración")
                    }
                }
                isNfcActive -> {
                    Text(
                        "🟢 Escaneo Activo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Acerca tu teléfono al de otro participante...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {
                    Text(
                        "⚪ Escaneo en Pausa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Presiona para comenzar a leer etiquetas",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (isNfcAvailable && isNfcEnabled) {
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { onToggleNfc(!isNfcActive) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isNfcActive) "Detener Escaneo" else "Iniciar Escaneo")
                }
            }
        }
    }
}

@Composable
fun ParticipantNFCCard(
    participant: UserProfile,
    isConfirmed: Boolean,
    onDebugSimulate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfirmed) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile photo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (!participant.profilePictureUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = participant.profilePictureUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        participant.nickname.take(1).uppercase(),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    participant.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (isConfirmed) {
                    Text(
                        "✅ Confirmado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "⏳ Pendiente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 🐛 DEBUG BUTTON (solo en modo debug)
            if (!isConfirmed && androidx.compose.ui.platform.LocalInspectionMode.current.not()) {
                OutlinedButton(
                    onClick = onDebugSimulate,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("🐛 Simular", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
