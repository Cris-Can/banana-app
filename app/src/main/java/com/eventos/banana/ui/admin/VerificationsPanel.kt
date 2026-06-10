package com.eventos.banana.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun VerificationsPanel(
    adminState: AdminDashboardViewModel.UiState,
    adminVm: AdminDashboardViewModel,
    onNavigateToProfile: (String) -> Unit
) {
    HorizontalDivider()
    Text(
        "Verificaciones de Identidad Pendientes",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        if (adminState.isLoadingVerifications) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (adminState.pendingVerifications.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("No hay solicitudes de verificación pendientes.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                adminState.pendingVerifications.forEach { pending ->
                    VerificationCard(
                        pending = pending,
                        onApprove = { adminVm.approveVerification(pending.user.uid) },
                        onReject = { adminVm.rejectVerification(pending.user.uid) },
                        onViewProfile = { onNavigateToProfile(pending.user.uid) },
                        onShowPhoto = { adminVm.showPhotoDialog(pending.user.uid) }
                    )
                }
            }
        }
    }
}
