package com.eventos.banana.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("banana_prefs", Context.MODE_PRIVATE) }

    // State for Toggles
    var notifyWall by remember { mutableStateOf(prefs.getBoolean("pref_notify_wall", true)) }
    var notifyMessages by remember { mutableStateOf(prefs.getBoolean("pref_notify_msg", true)) }
    var notifyGeneral by remember { mutableStateOf(prefs.getBoolean("pref_notify_general", true)) }

    // Save changes
    fun savePref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        // Here you could also sync with server if needed
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes de Notificaciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding) 
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Configura qué notificaciones quieres recibir en tu dispositivo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Wall Posts
            SettingsSwitch(
                title = "Publicaciones en Muro",
                description = "Notificar cuando alguien publica en un evento donde participas.",
                checked = notifyWall,
                onCheckedChange = {
                    notifyWall = it
                    savePref("pref_notify_wall", it)
                }
            )

            // Messages
            SettingsSwitch(
                title = "Mensajes Privados",
                description = "Notificar cuando recibes un nuevo mensaje de chat.",
                checked = notifyMessages,
                onCheckedChange = {
                    notifyMessages = it
                    savePref("pref_notify_msg", it)
                }
            )

            // General
            SettingsSwitch(
                title = "Avisos Generales",
                description = "Recordatorios de eventos y novedades de la app.",
                checked = notifyGeneral,
                onCheckedChange = {
                    notifyGeneral = it
                    savePref("pref_notify_general", it)
                }
            )


        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        // verticalAlignment = Alignment.CenterVertically // Default is top which is better for long text
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
