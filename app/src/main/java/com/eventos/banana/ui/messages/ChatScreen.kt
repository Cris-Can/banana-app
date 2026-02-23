package com.eventos.banana.ui.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import com.eventos.banana.domain.model.Message
import com.eventos.banana.util.AudioRecorderHelper
import com.eventos.banana.util.AudioPlayerHelper
import com.eventos.banana.R
import com.eventos.banana.ui.messages.ChatViewModel
import com.eventos.banana.ui.components.WaveformVisualizer
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    otherUserNickname: String,
    messages: List<Message>,
    currentUserId: String,
    viewModel: ChatViewModel, // 🆕 Add ViewModel
    themeColor: String? = null,
    isGold: Boolean = false,
    otherUserIsTyping: Boolean = false,
    onSendMessage: (String, String?) -> Unit,
    onTyping: (Boolean) -> Unit = {},
    onUpdateTheme: (String) -> Unit = {},
    onSendAudio: (ByteArray, Int, String?) -> Unit,
    onBack: () -> Unit,
    onReportUser: (String) -> Unit = {},
    onBlockUser: () -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onEditMessage: (String, String, String) -> Unit = { _, _, _ -> },
    onLoadMore: () -> Unit = {},
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

    // 📜 AUTO-SCROLL TO BOTTOM
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
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

    // 🎤 AUDIO & REPLY LOGIC (Migrated to ViewModel)
    val isRecording by viewModel.isRecording.collectAsState()
    val playingMessageId by viewModel.playingMessageId.collectAsState()
    val amplitudes by viewModel.amplitudes.collectAsState()
    val isSendingAudio by viewModel.isSendingAudio.collectAsState()
    val pendingAudioBytes by viewModel.pendingAudioBytes.collectAsState()
    val pendingAudioDuration by viewModel.pendingAudioDuration.collectAsState()
    var recordingDuration by remember { mutableLongStateOf(0L) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    
    val scope = rememberCoroutineScope()
    
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        }
    }

    // Timer for recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0L
            while (isRecording) {
                delay(1000)
                recordingDuration += 1000
            }
        }
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
                    val isCurrentUser = message.senderId == currentUserId
                    
                    // 🔄 Finder for quoted message
                    val quoted = remember(message.replyToId, messages) {
                        messages.find { it.id == message.replyToId }
                    }

                    MessageBubble(
                        message = message,
                        isCurrentUser = isCurrentUser,
                        activeColor = activeColor,
                        isGold = isGold,
                        onDelete = { onDeleteMessage(message.id) },
                        onEdit = { editingMessage = message },
                        onReply = { replyingTo = message },
                        onPlayAudio = { url -> 
                            viewModel.playAudio(message.id, url)
                        },
                        isPlaying = playingMessageId == message.id,
                        quotedMessage = quoted,
                        currentUserId = currentUserId, // 🆕 Pass currentUserId
                        onToggleReaction = { emoji ->
                            viewModel.toggleReaction(message.id, emoji)
                        }
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // ↩️ REPLY PREVIEW
                replyingTo?.let { reply ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Respondiendo a:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = activeColor ?: MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    if (reply.audioUrl != null) "🎤 Audio" else reply.content,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { replyingTo = null }) {
                                Icon(Icons.Default.Close, "Cancelar", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                when {
                    // 📤 ESTADO: Enviando audio (upload en progreso)
                    isSendingAudio -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = activeColor ?: MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Enviando audio...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 🔴 ESTADO: Grabando (waveform en tiempo real)
                    isRecording -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Botón cancelar
                            IconButton(
                                onClick = { viewModel.cancelRecording() }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Cancelar grabación",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            
                            // Waveform animado
                            WaveformVisualizer(
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                amplitudes = amplitudes,
                                color = MaterialTheme.colorScheme.error
                            )
                            
                            // Timer
                            Text(
                                "${recordingDuration / 1000}s",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            
                            // Botón detener → preview
                            IconButton(
                                onClick = {
                                    viewModel.stopRecordingForPreview(recordingDuration.toInt())
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = activeColor ?: MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Terminar", tint = Color.White)
                            }
                        }
                    }

                    // 🎧 ESTADO: Preview del audio (antes de enviar)
                    pendingAudioBytes != null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Botón descartar
                            IconButton(
                                onClick = { viewModel.discardPendingAudio() }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Descartar audio",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            
                            // Waveform estático del audio grabado
                            WaveformVisualizer(
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                amplitudes = amplitudes,
                                color = activeColor ?: MaterialTheme.colorScheme.primary
                            )
                            
                            // Duración
                            Text(
                                "${pendingAudioDuration / 1000}s",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            
                            // Botón enviar
                            IconButton(
                                onClick = {
                                    viewModel.confirmSendAudio(replyToId = replyingTo?.id)
                                    replyingTo = null
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = activeColor ?: MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Enviar audio", tint = Color.White)
                            }
                        }
                    }

                    // ✏️ ESTADO: Normal (texto o micrófono)
                    else -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { 
                                    messageText = it
                                    onTyping(it.isNotBlank())
                                },
                                placeholder = { Text(stringResource(com.eventos.banana.R.string.messages_placeholder)) },
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeColor ?: MaterialTheme.colorScheme.primary,
                                    cursorColor = activeColor ?: MaterialTheme.colorScheme.primary
                                )
                            )
                            
                            Spacer(Modifier.width(8.dp))
                            
                            if (messageText.isBlank()) {
                                // 🎤 Botón de grabar audio
                                IconButton(
                                    onClick = {
                                        val permission = android.Manifest.permission.RECORD_AUDIO
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            viewModel.startRecording()
                                        } else {
                                            permissionLauncher.launch(permission)
                                        }
                                    }
                                ) {
                                    Text("🎤", style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                // ✈️ Botón de enviar texto
                                IconButton(
                                    onClick = {
                                        if (com.eventos.banana.util.TextSafetyUtils.containsToxicContent(messageText)) {
                                            android.widget.Toast.makeText(context, "⚠️ Mensaje bloqueado", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            onSendMessage(messageText, replyingTo?.id)
                                            replyingTo = null
                                            messageText = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = activeColor ?: MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
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
    onReply: () -> Unit,
    onPlayAudio: (String) -> Unit,
    isPlaying: Boolean,
    quotedMessage: Message? = null,
    currentUserId: String, // 🆕 Added
    onToggleReaction: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) } // 🆕 Emoji picker state

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
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = {
                            if (!message.isDeleted) showMenu = true
                        }
                    )
                    // 🆕 Swipe-to-reply
                    .offset {
                        val x = if (!message.isDeleted) {
                            // Basic swipe detection logic
                            0.dp // To be implemented with drag gestures
                        } else 0.dp
                        androidx.compose.ui.unit.IntOffset(0, 0) 
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // ↩️ Quoted Message Display
                    if (quotedMessage != null && !message.isDeleted) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = if (quotedMessage.audioUrl != null) "🎤 Audio" else quotedMessage.content,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    if (message.audioUrl != null && !message.isDeleted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onPlayAudio(message.audioUrl) }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Close else Icons.Filled.PlayArrow,
                                    contentDescription = "Audio",
                                    tint = textColor
                                )
                            }
                            // 🆕 WAVEFORM VISUALIZER
                            WaveformVisualizer(
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                color = textColor,
                                seed = message.id
                            )
                            Text(
                                "${(message.audioDurationMs ?: 0) / 1000}s",
                                color = textColor,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Text(
                            text = displayContent,
                            color = textColor,
                            style = if (message.isDeleted) androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) else LocalTextStyle.current
                        )
                    }
                    
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
            
            // ❤️ REACTIONS DISPLAY
            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.reactions.forEach { (emoji, users) ->
                        val hasMyReaction = users.contains(currentUserId) // ✅ Corrected logic
                        Surface(
                            shape = CircleShape,
                            color = if (hasMyReaction) (activeColor?.copy(alpha = 0.2f) ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onToggleReaction(emoji) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(emoji, style = MaterialTheme.typography.labelSmall)
                                if (users.size > 1) {
                                    Spacer(Modifier.width(2.dp))
                                    Text(users.size.toString(), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // 👁️ READ RECEIPTS
            if (isCurrentUser && !message.isDeleted) {
                val isRead = message.readers.isNotEmpty()
                val checkColor = if (isGold && isRead) androidx.compose.ui.graphics.Color(0xFF2196F3) else androidx.compose.ui.graphics.Color.Gray
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, end = 4.dp)) {
                    Text(
                        com.eventos.banana.ui.messages.formatTimestampShort(message.timestamp), 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Read Status",
                        tint = checkColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        
        var showDeleteDialog by remember { mutableStateOf(false) }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            // 🆕 Direct Emoji Row in Menu
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("❤️", "😂", "😮", "🔥", "👍", "🍌").forEach { emoji ->
                            Text(
                                emoji,
                                modifier = Modifier.clickable { 
                                    onToggleReaction(emoji)
                                    showMenu = false 
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                },
                onClick = { }
            )
            Divider()
            DropdownMenuItem(
                text = { Text("↩️ Responder") },
                onClick = { showMenu = false; onReply() }
            )
            if (isCurrentUser) {
                DropdownMenuItem(
                    text = { Text("✏️ Editar") },
                    onClick = { showMenu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("🗑️ Eliminar", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; showDeleteDialog = true }
                )
            }
        }

        // 🆕 EMOJI PICKER POPUP
        if (showEmojiPicker) {
            EmojiPickerPopup(
                onDismiss = { showEmojiPicker = false },
                onEmojiSelected = { emoji ->
                    onToggleReaction(emoji)
                    showEmojiPicker = false
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Eliminar mensaje") },
                text = { Text("¿Estás seguro de que deseas eliminar este mensaje para todos?") },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun EmojiPickerPopup(onDismiss: () -> Unit, onEmojiSelected: (String) -> Unit) {
    val emojis = listOf("❤️", "😂", "😮", "😢", "🔥", "👍", "🍌")
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                emojis.forEach { emoji ->
                    Text(
                        emoji,
                        modifier = Modifier
                            .clickable { onEmojiSelected(emoji) }
                            .padding(8.dp),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}
