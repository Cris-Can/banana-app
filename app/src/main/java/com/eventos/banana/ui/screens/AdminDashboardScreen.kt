package com.eventos.banana.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.eventos.banana.ui.admin.AdminDashboardViewModel
import com.eventos.banana.ui.admin.ExternalEventsViewModel
import com.eventos.banana.ui.admin.AdminToolsViewModel
import com.eventos.banana.ui.admin.FounderCodesPanel
import com.eventos.banana.ui.admin.ReportsPanel
import com.eventos.banana.ui.admin.VerificationsPanel
import com.eventos.banana.ui.admin.ExternalEventsPanel
import com.eventos.banana.ui.admin.MaintenancePanel
import com.eventos.banana.ui.admin.ApprovalDialog
import androidx.compose.ui.platform.LocalUriHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    currentUserId: String,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToMap: (Double?, Double?) -> Unit,
    adminVm: AdminDashboardViewModel = hiltViewModel(),
    externalVm: ExternalEventsViewModel = hiltViewModel(),
    toolsVm: AdminToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val adminState by adminVm.uiState.collectAsState()
    val extState by externalVm.state.collectAsState()
    val toolsState by toolsVm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val categorias = listOf(
        "CONCIERTO", "FESTIVAL", "CHARLA", "TALLER", "TEATRO",
        "CINE", "DEPORTE", "FERIA", "MUSICA", "ARTE", "GASTRONOMIA", "OTRO"
    )

    LaunchedEffect(Unit) {
        adminVm.loadData()
        externalVm.loadExternalSources()
        externalVm.loadPendingEvents()
    }

    LaunchedEffect(adminState.snackbarMessage, extState.snackbarMessage, toolsState.snackbarMessage) {
        val msg = adminState.snackbarMessage ?: extState.snackbarMessage ?: toolsState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            when {
                adminState.snackbarMessage != null -> adminVm.dismissSnackbar()
                extState.snackbarMessage != null -> externalVm.dismissSnackbar()
                toolsState.snackbarMessage != null -> toolsVm.dismissSnackbar()
            }
        }
    }
    
    val durationOptions = listOf(
        Pair(30, "1 Mes"),
        Pair(90, "3 Meses"),
        Pair(180, "6 Meses"),
        Pair(null, "Eterno")
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("👮 Admin Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            
            FounderCodesPanel(toolsState, toolsVm, currentUserId, durationOptions)

            HorizontalDivider()

            ReportsPanel(adminState, adminVm, onNavigateToProfile)

            VerificationsPanel(adminState, adminVm, onNavigateToProfile)

            // Dialog para foto en grande (moved out of Box so it works with scroll parent)
            if (adminState.showPhotoDialogUid != null) {
                AlertDialog(
                    onDismissRequest = { adminVm.dismissPhotoDialog() },
                    confirmButton = {
                        TextButton(onClick = { adminVm.dismissPhotoDialog() }) {
                            Text("Cerrar")
                        }
                    },
                    title = { Text("Foto de Identificación") },
                    text = {
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (adminState.photoUrl != null) {
                                AsyncImage(
                                    model = adminState.photoUrl,
                                    contentDescription = "ID Photo",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator()
                            }
                        }
                    }
                )
            }

            ExternalEventsPanel(extState, externalVm, onNavigateToMap)

            MaintenancePanel(toolsState, toolsVm)
        }
        
        ApprovalDialog(extState, externalVm, onNavigateToMap, categorias, uriHandler, context)
    }

    if (toolsState.showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { if (!toolsState.isCleaning) toolsVm.showCleanupConfirm(false) },
            title = { Text("¿Confirmar limpieza profunda?") },
            text = { 
                Text("Esta acción eliminará campos redundantes (gold, premium, averageRating, etc) de TODOS los usuarios. Es irreversible.")
            },
            confirmButton = {
                TextButton(
                    onClick = { toolsVm.cleanupDatabase() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("LIMPIAR AHORA")
                }
            },
            dismissButton = {
                TextButton(onClick = { toolsVm.showCleanupConfirm(false) }, enabled = !toolsState.isCleaning) {
                    Text("Cancelar")
                }
            }
        )
    }


}
