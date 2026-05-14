package com.eventos.banana.ui.screens

import kotlinx.coroutines.launch
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventos.banana.ui.profile.UserViewModel
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    currentUserId: String,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    userViewModel: UserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var reports by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Founder Codes State
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var generateError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Maintenance State
    var showCleanupConfirm by remember { mutableStateOf(false) }
    var isCleaning by remember { mutableStateOf(false) }

    // Mostrar errores vía Snackbar
    LaunchedEffect(generateError) {
        if (generateError != null) {
            snackbarHostState.showSnackbar(generateError!!)
            generateError = null
        }
    }
    
    // Duration options: 30, 90, 180, null (Lifetime)
    var selectedDurationDays by remember { mutableStateOf<Int?>(30) }
    val durationOptions = listOf(
        Pair(30, "1 Mes"),
        Pair(90, "3 Meses"),
        Pair(180, "6 Meses"),
        Pair(null, "Eterno")
    )
    
    // Load Reports
    LaunchedEffect(Unit) {
        reports = userViewModel.getPendingReports()
        isLoading = false
    }
    
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
        ) {
            
            // --- FOUNDER CODES ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Gestión de Invitaciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    
                    if (generatedCode != null) {
                        Text("Nuevo Código Founder Generado:", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = generatedCode!!,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        val durText = durationOptions.find { it.first == selectedDurationDays }?.second ?: "Eterno"
                        Text("Duración asignada: $durText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { generatedCode = null }) {
                            Text("Generar Otro")
                        }
                    } else {
                        Text("Elegir duración de la suscripción regalo:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(durationOptions) { (days, label) ->
                                val isSelected = days == selectedDurationDays
                                Surface(
                                    modifier = Modifier.clickable { selectedDurationDays = days },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                isGenerating = true
                                scope.launch {
                                    val result = userViewModel.generateFounderCode(currentUserId, selectedDurationDays)
                                    if (result.isSuccess) {
                                        generatedCode = result.getOrNull()
                                    } else {
                                        generateError = "❌ Error: ${result.exceptionOrNull()?.message ?: "No se pudo crear el código"}"
                                    }
                                    isGenerating = false
                                }
                            },
                            enabled = !isGenerating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Generar Código Founder")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- REPORTS ---
            Box(modifier = Modifier.fillMaxSize().weight(1f, fill = false)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (reports.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Todo limpio. No hay reportes pendientes.", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(reports) { report ->
                            ReportCard(
                                report = report,
                                onResolve = { id, action ->
                                    scope.launch {
                                        userViewModel.resolveReport(id, action)
                                        reports = reports.filter { it["id"] != id }
                                    }
                                },
                                onBan = { uid, reportId ->
                                    scope.launch {
                                        userViewModel.banUser(uid)
                                        userViewModel.resolveReport(reportId, "BANNED")
                                        reports = reports.filter { it["id"] != reportId }
                                    }
                                },
                                onViewProfile = onNavigateToProfile
                            )
                        }
                    }
                }
            }

            // --- MANTENIMIENTO ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Mantenimiento", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Elimina campos obsoletos y redundantes de la base de datos de usuarios para mejorar el rendimiento.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showCleanupConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCleaning
                    ) {
                        if (isCleaning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError)
                        } else {
                            Text("Limpiar Base de Datos")
                        }
                    }
                }
            }
        }
    }

    if (showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isCleaning) showCleanupConfirm = false },
            title = { Text("¿Confirmar limpieza profunda?") },
            text = { 
                Text("Esta acción eliminará campos redundantes (gold, premium, averageRating, etc) de TODOS los usuarios. Es irreversible.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCleanupConfirm = false
                        isCleaning = true
                        scope.launch {
                            val result = userViewModel.cleanupUsersDatabase()
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("✅ ${result.getOrNull()}")
                            } else {
                                snackbarHostState.showSnackbar("❌ Error: ${result.exceptionOrNull()?.message}")
                            }
                            isCleaning = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("LIMPIAR AHORA")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupConfirm = false }, enabled = !isCleaning) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ReportCard(
    report: Map<String, Any>,
    onResolve: (String, String) -> Unit,
    onBan: (String, String) -> Unit,
    onViewProfile: (String) -> Unit
) {
    val id = report["id"] as? String ?: return
    val reason = report["reason"] as? String ?: "Sin razón"
    val reportedId = report["reportedId"] as? String ?: ""
    val reporterId = report["reporterId"] as? String ?: ""
    val timestamp = (report["timestamp"] as? com.google.firebase.Timestamp)?.toDate() ?: Date()
    val dateStr = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(timestamp)

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Reporte: $reason", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            
            Text("Acusado (Reported): $reportedId", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text("Reportado por: $reporterId", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Fecha: $dateStr", style = MaterialTheme.typography.labelSmall)
            
            Spacer(Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onViewProfile(reportedId) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ver Perfil")
                }
                
                Button(
                    onClick = { onBan(reportedId, id) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("BANEAR")
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            TextButton(
                onClick = { onResolve(id, "IGNORED") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Descartar (Ignorar)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
