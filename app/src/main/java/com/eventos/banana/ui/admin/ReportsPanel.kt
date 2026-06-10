package com.eventos.banana.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReportsPanel(
    adminState: AdminDashboardViewModel.UiState,
    adminVm: AdminDashboardViewModel,
    onNavigateToProfile: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (adminState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (adminState.reports.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Todo limpio. No hay reportes pendientes.", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                adminState.reports.forEach { report ->
                    ReportCard(
                        report = report,
                        onResolve = { id, action -> adminVm.resolveReport(id, action) },
                        onBan = { uid, reportId -> adminVm.banUser(uid, reportId) },
                        onViewProfile = onNavigateToProfile
                    )
                }
            }
        }
    }
}
