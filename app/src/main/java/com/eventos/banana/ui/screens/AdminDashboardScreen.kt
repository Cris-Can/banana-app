package com.eventos.banana.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.ui.components.BananaButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userRepo = remember { UserRepository() }
    
    var reports by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load Reports
    LaunchedEffect(Unit) {
        reports = userRepo.getPendingReports()
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
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            } else if (reports.isEmpty()) {
                Column(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
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
                                    userRepo.resolveReport(id, action)
                                    // Refresh list locally
                                    reports = reports.filter { it["id"] != id }
                                }
                            },
                            onBan = { uid, reportId ->
                                scope.launch {
                                    userRepo.banUser(uid)
                                    userRepo.resolveReport(reportId, "BANNED")
                                    reports = reports.filter { it["id"] != reportId }
                                }
                            },
                            onViewProfile = onNavigateToProfile
                        )
                    }
                }
            }
        }
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
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
