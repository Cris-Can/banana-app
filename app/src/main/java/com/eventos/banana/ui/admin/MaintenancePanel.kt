package com.eventos.banana.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MaintenancePanel(
    toolsState: AdminToolsUiState,
    toolsVm: AdminToolsViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
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
                onClick = { toolsVm.showCleanupConfirm(true) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                enabled = !toolsState.isCleaning
            ) {
                if (toolsState.isCleaning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError)
                } else {
                    Text("Limpiar Base de Datos")
                }
            }
        }
    }
}
