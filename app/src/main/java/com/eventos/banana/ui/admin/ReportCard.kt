package com.eventos.banana.ui.admin

import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

            Text("Acusado (Reported): $reportedId", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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
