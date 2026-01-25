package com.eventos.banana.ui.event

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventos.banana.util.NFCManager
import com.eventos.banana.viewmodel.NFCEncounterViewModel
import com.eventos.banana.viewmodel.NFCEncounterViewModelFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NFCTapScreen(
    eventId: String,
    currentUserId: String,
    participantIds: List<String>,
    onBack: () -> Unit,
    viewModel: NFCEncounterViewModel = viewModel(
        factory = NFCEncounterViewModelFactory(eventId, currentUserId, participantIds)
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val nfcManager = remember { NFCManager(context as Activity) }

    // Lifecycle for NFC Reader Mode
    DisposableEffect(Unit) {
        if (nfcManager.isNfcAvailable() && nfcManager.isNfcEnabled()) {
            nfcManager.enableReaderMode { detectedId ->
                viewModel.recordNFCEncounter(detectedId)
            }
            viewModel.setNfcActive(true)
        }
        viewModel.setNfcStatus(
            available = nfcManager.isNfcAvailable(),
            enabled = nfcManager.isNfcEnabled()
        )

        onDispose {
            nfcManager.disableReaderMode()
        }
    }
    
    // Snackbar for messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmar Encuentros") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isNfcActive) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.isNfcActive) {
                        Text("🟢 NFC Activado", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Acerca tu teléfono al de otro participante para confirmar que se conocieron.",
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text("🔴 NFC Desactivado", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        if (!uiState.nfcAvailable) {
                            Text("Tu dispositivo no soporta NFC.", textAlign = TextAlign.Center)
                        } else {
                            Text("Activa NFC en la configuración de tu teléfono.", textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
                                } catch (e: Exception) {
                                    // Fallback to general settings
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
                                }
                            }) {
                                Text("Ir a Configuración")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            Text(
                "Participantes (${uiState.confirmedEncounters.size}/${uiState.participants.size} confirmados)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Spacer(Modifier.height(8.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.participants) { user ->
                        val isConfirmed = uiState.confirmedEncounters.contains(user.uid)
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isConfirmed) 
                                    MaterialTheme.colorScheme.surfaceVariant 
                                else 
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = if (isConfirmed) 
                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                            else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(user.nickname ?: "Usuario", style = MaterialTheme.typography.bodyLarge)
                                
                                if (isConfirmed) {
                                    Icon(
                                        Icons.Default.CheckCircle, 
                                        contentDescription = "Confirmado",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text("Pendiente", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                    
                    if (uiState.participants.isEmpty()) {
                        item {
                            Text(
                                "No hay otros participantes aún.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
