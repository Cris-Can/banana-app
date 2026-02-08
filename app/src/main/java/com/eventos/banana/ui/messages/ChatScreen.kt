package com.eventos.banana.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    otherUserNickname: String,
    messages: List<Message>,
    currentUserId: String,
    themeColor: String? = null,
    isGold: Boolean = false, // 💎 Gold Feature
    onSendMessage: (String) -> Unit,
    onUpdateTheme: (String) -> Unit = {},
    onBack: () -> Unit,
    onReportUser: (String) -> Unit = {},
    onBlockUser: () -> Unit = {}
) {
    var messageText by remember { mutableStateOf("") }
    var showColorPicker by remember { mutableStateOf(false) }
    var showUpsellDialog by remember { mutableStateOf(false) }
    
    // 🎨 Resolve Theme Color
    val activeColor = remember(themeColor) {
        if (themeColor != null && themeColor.startsWith("#")) {
            try {
                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(themeColor))
            } catch (e: Exception) {
                null // Fallback to primary
            }
        } else {
            null
        }
    }
    
    // Fallback to MaterialTheme.colorScheme.primary if activeColor is null, but we need it inside Composable for MessageBubble
    
    if (showColorPicker) {
        ColorPickerDialog(
            onDismiss = { showColorPicker = false },
            onColorSelected = { colorHex ->
                onUpdateTheme(colorHex)
                showColorPicker = false
            }
        )
    }

    // Upsell dialog removed (Themes are now free)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherUserNickname) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    // 🎨 Theme Button (Open for everyone now)
                    IconButton(onClick = {
                        showColorPicker = true
                    }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Edit, "Tema",
                            tint = activeColor ?: MaterialTheme.colorScheme.primary
                        )
                    }

                    // 🛡️ Block/Report Menu (Round 49)
                    var showMenu by remember { mutableStateOf(false) }
                    var showReportDialog by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, "Opciones")
                    }
                    
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("🚩 Reportar") },
                            onClick = { showMenu = false; showReportDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("🚫 Bloquear") },
                            onClick = { 
                                showMenu = false
                                onBlockUser()
                            }
                        )
                    }
                    
                    if (showReportDialog) {
                         // Simple Report Dialog
                         AlertDialog(
                             onDismissRequest = { showReportDialog = false },
                             title = { Text("Denunciar chat") },
                             text = { Text("¿Denunciar este chat por contenido inapropiado?") },
                             confirmButton = {
                                 TextButton(onClick = {
                                     onReportUser("Contenido inapropiado")
                                     showReportDialog = false
                                 }) { Text("Denunciar", color = MaterialTheme.colorScheme.error) }
                             },
                             dismissButton = {
                                 TextButton(onClick = { showReportDialog = false }) { Text("Cancelar") }
                             }
                         )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        isCurrentUser = message.senderId == currentUserId,
                        activeColor = activeColor
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            
            // Input field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Escribe un mensaje...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeColor ?: MaterialTheme.colorScheme.primary,
                        cursorColor = activeColor ?: MaterialTheme.colorScheme.primary
                    )
                )
                
                Spacer(Modifier.width(8.dp))
                
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    enabled = messageText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, 
                        "Enviar",
                        tint = if (messageText.isNotBlank()) activeColor ?: MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha=0.38f)
                    )
                }
            }
        }
    }
}

@Composable
fun ColorPickerDialog(
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        Pair("Default", "DEFAULT"), // Null/Reset
        Pair("Rojo", "#F44336"),
        Pair("Azul", "#2196F3"),
        Pair("Verde", "#4CAF50"),
        Pair("Rosa", "#E91E63"),
        Pair("Naranja", "#FF9800"),
        Pair("Morado", "#9C27B0"),
        Pair("Negro", "#000000")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elige un color") },
        text = {
            Column {
                colors.chunked(4).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowColors.forEach { (name, hex) ->
                            val color = if (hex == "DEFAULT") MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
                            
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color, CircleShape)
                                    .clickable {
                                        onColorSelected(hex)
                                    }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun MessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    activeColor: androidx.compose.ui.graphics.Color? = null
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isCurrentUser) {
                activeColor ?: MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (isCurrentUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

