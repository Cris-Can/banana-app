package com.eventos.banana.ui.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.eventos.banana.domain.model.Message
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    otherUserNickname: String,
    messages: List<Message>,
    currentUserId: String,
    themeColor: String? = null,
    isGold: Boolean = false, // 💎 Gold Feature
    otherUserIsTyping: Boolean = false, // ⌨️ Typing Status
    onSendMessage: (String) -> Unit,
    onTyping: (Boolean) -> Unit = {},
    onUpdateTheme: (String) -> Unit = {},
    onBack: () -> Unit,
    onReportUser: (String) -> Unit = {},
    onBlockUser: () -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onEditMessage: (String, String, String) -> Unit = { _, _, _ -> },
    onLoadMore: () -> Unit = {}
) {
    var messageText by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showColorPicker by remember { mutableStateOf(false) } // Add state tracking for list
    val listState = androidx.compose.foundation.lazy.rememberLazyListState() // Track scroll state

    // 📜 INFINITE SCROLL LOGIC
    // Check if we are at the top (index 0) and trigger load more
    val isAtTop by remember {
        derivedStateOf {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
        }
    }

    LaunchedEffect(isAtTop) {
        if (isAtTop) {
            onLoadMore()
        }
    }
    
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
    
    // Debounce Typing Logic
    LaunchedEffect(messageText) {
        if (messageText.isNotEmpty()) {
            onTyping(true)
            kotlinx.coroutines.delay(3000) // Stop typing after 3s of inactivity
            onTyping(false)
        } else {
            onTyping(false)
        }
    }
    
    // Edit Message Dialog State
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    if (editingMessage != null) {
        EditMessageDialog(
            initialContent = editingMessage!!.content,
            onDismiss = { editingMessage = null },
            onConfirm = { newContent ->
                onEditMessage(editingMessage!!.id, editingMessage!!.content, newContent)
                editingMessage = null
            }
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            onDismiss = { showColorPicker = false },
            onColorSelected = { colorHex ->
                onUpdateTheme(colorHex)
                showColorPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(otherUserNickname)
                        // ⌨️ Typing Indicator (PREMIUM ONLY)
                        if (otherUserIsTyping && isGold) {
                            Text(
                                "Escribiendo... 🖊️",
                                style = MaterialTheme.typography.labelSmall,
                                color = activeColor ?: MaterialTheme.colorScheme.primary
                            )
                        } else if (otherUserIsTyping && !isGold) {
                            // Free users don't see typing indicator (Ghost feature)
                        }
                    } 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.eventos.banana.R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showColorPicker = true }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Edit, stringResource(com.eventos.banana.R.string.settings_theme),
                            tint = activeColor ?: MaterialTheme.colorScheme.primary
                        )
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    var showReportDialog by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, stringResource(com.eventos.banana.R.string.common_edit))
                    }
                    
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("🚩 " + stringResource(com.eventos.banana.R.string.messages_report_button)) },
                            onClick = { showMenu = false; showReportDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("🚫 " + stringResource(com.eventos.banana.R.string.profile_block)) },
                            onClick = { 
                                showMenu = false
                                onBlockUser()
                            }
                        )
                    }
                    
                    if (showReportDialog) {
                         AlertDialog(
                             onDismissRequest = { showReportDialog = false },
                             title = { Text(stringResource(com.eventos.banana.R.string.messages_report_title)) },
                             text = { Text(stringResource(com.eventos.banana.R.string.messages_report_body)) },
                             confirmButton = {
                                 TextButton(onClick = {
                                     onReportUser("Contenido inapropiado")
                                     showReportDialog = false
                                 }) { Text(stringResource(com.eventos.banana.R.string.messages_report_button), color = MaterialTheme.colorScheme.error) }
                             },
                             dismissButton = {
                                 TextButton(onClick = { showReportDialog = false }) { Text(stringResource(com.eventos.banana.R.string.common_cancel)) }
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
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                state = listState, // Attach state
                reverseLayout = false
            ) {
                items(
                    items = messages,
                    key = { it.id } // 🔑 Unique key for animations
                ) { message ->
                    MessageBubble(
                        message = message,
                        isCurrentUser = message.senderId == currentUserId,
                        activeColor = activeColor,
                        isGold = isGold, // Pass gold status for Ghost Mode
                        onDelete = { onDeleteMessage(message.id) },
                        onEdit = { editingMessage = message },
                        modifier = Modifier // Animation disabled temporarily due to build error
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text(stringResource(com.eventos.banana.R.string.messages_placeholder)) },
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
                            if (com.eventos.banana.util.TextSafetyUtils.containsToxicContent(messageText)) {
                                // 🚫 Block message locally (Using valid context)
                                android.widget.Toast.makeText(context, "⚠️ Mensaje bloqueado por contenido inapropiado", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        }
                    },
                    enabled = messageText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, 
                        stringResource(com.eventos.banana.R.string.common_send),
                        tint = if (messageText.isNotBlank()) activeColor ?: MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha=0.38f)
                    )
                }
            }
        }
    }
}

@Composable
fun EditMessageDialog(initialContent: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialContent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar mensaje") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ColorPickerDialog(
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        Pair("Default", "DEFAULT"),
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
        title = { Text(stringResource(com.eventos.banana.R.string.messages_pick_color)) },
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
                Text(stringResource(com.eventos.banana.R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun MessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    activeColor: androidx.compose.ui.graphics.Color? = null,
    isGold: Boolean,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier // Receive modifier from parent for animation
) {
    var showMenu by remember { mutableStateOf(false) }

    // 👻 GHOST MODE LOGIC
    // If message is deleted:
    // - Free Users see: "🚫 Mensaje eliminado"
    // - Gold Users see: "🗑️ [Texto Original]" (Red/Grey)
    
    val displayContent = if (message.isDeleted) {
        if (isGold) "👻 Eliminado: ${message.content}" else "🚫 Mensaje eliminado"
    } else {
        message.content
    }
    
    val bubbleColor = if (message.isDeleted) {
        if (isGold) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    } else {
        if (isCurrentUser) activeColor ?: MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    }
    
    val textColor = if (message.isDeleted) {
         if (isGold) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    } else {
         if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier.fillMaxWidth(), // Apply animateItemPlacement here via modifier
        contentAlignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    // Context Menu for owner
                    .combinedClickable(
                        onClick = { },
                        onLongClick = {
                            if (isCurrentUser && !message.isDeleted) showMenu = true
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = displayContent,
                        color = textColor,
                        style = if (message.isDeleted) androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) else LocalTextStyle.current
                    )
                    
                    // 🏷️ Edited Label
                    if (message.isEdited && !message.isDeleted) {
                        Text(
                            "Editado",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
            
            // 👁️ READ RECEIPTS (Double Check) - Only visible to sender
            if (isCurrentUser && !message.isDeleted) {
                val isRead = message.readers.isNotEmpty() // Simplified check
                // Premium: Blue if read, Grey if not
                // Free: Always Grey
                val checkColor = if (isGold && isRead) androidx.compose.ui.graphics.Color(0xFF2196F3) else androidx.compose.ui.graphics.Color.Gray
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, end = 4.dp)) {
                    Text(
                        com.eventos.banana.ui.messages.formatTimestampShort(message.timestamp), 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isRead) Icons.Default.Check else Icons.Default.Check, // Double check simulated logic
                        contentDescription = "Read Status",
                        tint = checkColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("✏️ Editar") },
                onClick = { showMenu = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("🗑️ Eliminar", color = MaterialTheme.colorScheme.error) },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }
}
