package com.eventos.banana.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun VerificationCard(
    pending: PendingVerification,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onViewProfile: () -> Unit,
    onShowPhoto: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Thumbnail de la foto
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clickable(onClick = onShowPhoto),
                    contentAlignment = Alignment.Center
                ) {
                    if (pending.photoUrl != null) {
                        AsyncImage(
                            model = pending.photoUrl,
                            contentDescription = "ID Photo",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(pending.user.nickname.ifBlank { "Sin nickname" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(pending.user.uid, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text("Toca la foto para ver en grande", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onViewProfile,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ver Perfil")
                }
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Rechazar")
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Aceptar")
                }
            }
        }
    }
}
