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
import com.eventos.banana.domain.model.ConversationTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.graphics.Brush

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    otherUserNickname: String,
    messages: List<Message>,
    currentUserId: String,
    viewModel: ChatViewModel, // 🆕 Add ViewModel
    chatTheme: ConversationTheme = ConversationTheme(),
    isGold: Boolean = false,
    otherUserIsTyping: Boolean = false,
    onSendMessage: (String, String?) -> Unit,
    onTyping: (Boolean) -> Unit = {},
    onOpenThemeConfig: () -> Unit = {},
    onSendAudio: (ByteArray, Int, String?) -> Unit,
    onBack: () -> Unit,
    onReportUser: (String) -> Unit = {},
    onBlockUser: () -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onEditMessage: (String, String, String) -> Unit = { _, _, _ -> },
    onLoadMore: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    var messageText by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
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
    val activeColor = remember(chatTheme.primaryColor) {
        chatTheme.primaryColor?.let {
            try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) } catch(e: Exception) { null }
        }
    }
    val secondaryColor = remember(chatTheme.secondaryColor) {
        chatTheme.secondaryColor?.let {
            try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) } catch(e: Exception) { null }
        }
    }
    val bgColor = remember(chatTheme.backgroundColor) {
        chatTheme.backgroundColor?.let {
            try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) } catch(e: Exception) { null }
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

    val topBarModifier = if (chatTheme.headerStyle == "gradient") {
        Modifier.background(Brush.horizontalGradient(
            colors = listOf(activeColor ?: MaterialTheme.colorScheme.primary, secondaryColor ?: activeColor ?: MaterialTheme.colorScheme.primary)
        ))
    } else Modifier

    val topBarColors = if (chatTheme.headerStyle == "gradient") {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    } else if (chatTheme.headerStyle == "minimal") {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = topBarModifier,
                colors = topBarColors,
                title = { 
                    Column(
                        modifier = Modifier.clickable { onProfileClick() }
                    ) {
                        Text(otherUserNickname)
                        // ⌨️ Typing Indicator (PREMIUM ONLY)
                        if (otherUserIsTyping && isGold) {
                            when (chatTheme.typingStyle) {
                                "classic" -> {
                                    Text(
                                        "Escribiendo...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (chatTheme.headerStyle == "gradient") MaterialTheme.colorScheme.onPrimary else (activeColor ?: MaterialTheme.colorScheme.primary)
                                    )
                                }
                                "pulse" -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Escribiendo ", style = MaterialTheme.typography.labelSmall, color = if (chatTheme.headerStyle == "gradient") MaterialTheme.colorScheme.onPrimary else (activeColor ?: MaterialTheme.colorScheme.primary))
                                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                        val alpha1 by infiniteTransition.animateFloat(initialValue = 0.2f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(300, delayMillis = 0), RepeatMode.Reverse), label = "d1")
                                        val alpha2 by infiniteTransition.animateFloat(initialValue = 0.2f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(300, delayMillis = 150), RepeatMode.Reverse), label = "d2")
                                        val alpha3 by infiniteTransition.animateFloat(initialValue = 0.2f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(300, delayMillis = 300), RepeatMode.Reverse), label = "d3")
                                        Text(".", style = MaterialTheme.typography.labelSmall, color = (if (chatTheme.headerStyle == "gradient") MaterialTheme.colorScheme.onPrimary else (activeColor ?: MaterialTheme.colorScheme.primary)).copy(alpha = alpha1))
                                        Text(".", style = MaterialTheme.typography.labelSmall, color = (if (chatTheme.headerStyle == "gradient") MaterialTheme.colorScheme.onPrimary else (activeColor ?: MaterialTheme.colorScheme.primary)).copy(alpha = alpha2))
                                        Text(".", style = MaterialTheme.typography.labelSmall, color = (if (chatTheme.headerStyle == "gradient") MaterialTheme.colorScheme.onPrimary else (activeColor ?: MaterialTheme.colorScheme.primary)).copy(alpha = alpha3))
                                    }
                                }
                                else -> {
                                    Text(
                                        "Escribiendo... \uD83D\uDD8A\uFE0F",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (chatTheme.headerStyle == "gradient") MaterialTheme.colorScheme.onPrimary else (activeColor ?: MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
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
                    IconButton(onClick = onOpenThemeConfig) {
                        Icon(androidx.compose.material.icons.Icons.Default.Edit, stringResource(com.eventos.banana.R.string.settings_theme),
                            tint = if (chatTheme.headerStyle == "gradient") MaterialTheme.colorScheme.onPrimary else (activeColor ?: MaterialTheme.colorScheme.primary)
                        )
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    var showReportDialog by remember { mutableStateOf(false) }
                    var showBlockDialog by remember { mutableStateOf(false) }
                    
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
                                showBlockDialog = true
                            }
                        )
                    }
                    
                    if (showBlockDialog) {
                        AlertDialog(
                            onDismissRequest = { showBlockDialog = false },
                            title = { Text("Bloquear Usuario") },
                            text = { Text("¿Estás seguro de que deseas bloquear a $otherUserNickname? Ya no podrán enviarte mensajes ni ver tu perfil.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    onBlockUser()
                                    showBlockDialog = false
                                }) { Text("Bloquear", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showBlockDialog = false }) { Text(stringResource(com.eventos.banana.R.string.common_cancel)) }
                            }
                        )
                    }

                    if (showReportDialog) {
                        var selectedReason by remember { mutableStateOf("") }
                        var customReason by remember { mutableStateOf("") }
                        val reasons = listOf(
                            "Contenido inapropiado",
                            "Spam o publicidad",
                            "Acoso o bullying",
                            "Suplantación de identidad",
                            "Otro"
                        )
                         AlertDialog(
                             onDismissRequest = { showReportDialog = false },
                             title = { Text(stringResource(com.eventos.banana.R.string.messages_report_title)) },
                             text = {
                                 Column {
                                     Text(
                                         stringResource(com.eventos.banana.R.string.messages_report_body),
                                         style = MaterialTheme.typography.bodyMedium
                                     )
                                     Spacer(modifier = Modifier.height(12.dp))
                                     reasons.forEach { reason ->
                                         Row(
                                             verticalAlignment = Alignment.CenterVertically,
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .clickable { selectedReason = reason }
                                                 .padding(vertical = 4.dp)
                                         ) {
                                             RadioButton(
                                                 selected = selectedReason == reason,
                                                 onClick = { selectedReason = reason }
                                             )
                                             Text(reason, style = MaterialTheme.typography.bodyMedium)
                                         }
                                     }
                                     if (selectedReason == "Otro") {
                                         OutlinedTextField(
                                             value = customReason,
                                             onValueChange = { customReason = it },
                                             placeholder = { Text("Describe el motivo...") },
                                             modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                             maxLines = 2
                                         )
                                     }
                                 }
                             },
                             confirmButton = {
                                 TextButton(
                                     onClick = {
                                         val finalReason = if (selectedReason == "Otro") customReason.ifBlank { "Otro" } else selectedReason
                                         onReportUser(finalReason)
                                         showReportDialog = false
                                     },
                                     enabled = selectedReason.isNotBlank()
                                 ) { Text(stringResource(com.eventos.banana.R.string.messages_report_button), color = MaterialTheme.colorScheme.error) }
                             },
                             dismissButton = {
                                 TextButton(onClick = { showReportDialog = false }) { Text(stringResource(com.eventos.banana.R.string.common_cancel)) }
                             }
                         )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor ?: MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            val seenMessages = remember { mutableStateOf(setOf<String>()) }
            val messagesById = remember(messages) {
                messages.associateBy { it.id }
            }
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
                    val isNew = remember { !seenMessages.value.contains(message.id) }
                    LaunchedEffect(message.id) {
                        if (isNew) {
                            seenMessages.value = seenMessages.value + message.id
                        }
                    }
                    
                    val enterAnim = when (chatTheme.bubbleAnimation) {
                        "fade" -> fadeIn()
                        "scale" -> scaleIn()
                        else -> slideInVertically { it } + fadeIn()
                    }

                    AnimatedVisibility(
                        visibleState = remember { androidx.compose.animation.core.MutableTransitionState(if (isNew) false else true).apply { targetState = true } },
                        enter = enterAnim
                    ) {
                        Column {
                            val isCurrentUser = message.senderId == currentUserId
                            // 🔄 Finder for quoted message
                            val quoted = if (message.replyToId != null) {
                                messagesById.get(message.replyToId)
                            } else null
                            val onDelete = remember(message.id) { { onDeleteMessage(message.id) } }
                            val onEdit = remember(message) { { editingMessage = message } }
                            val onReply = remember(message) { { replyingTo = message } }
                            val onPlayAudio = remember(message.id) { { url: String -> viewModel.playAudio(message.id, url) } }
                            val onToggleReaction = remember(message.id) { { emoji: String -> viewModel.toggleReaction(message.id, emoji) } }

                            MessageBubble(
                                message = message,
                                isCurrentUser = isCurrentUser,
                                activeColor = activeColor,
                                secondaryColor = secondaryColor,
                                chatTheme = chatTheme,
                                isGold = isGold,
                                onDelete = onDelete,
                                onEdit = onEdit,
                                onReply = onReply,
                                onPlayAudio = onPlayAudio,
                                isPlaying = playingMessageId == message.id,
                                quotedMessage = quoted,
                                currentUserId = currentUserId, // 🆕 Pass currentUserId
                                onToggleReaction = onToggleReaction
                            )
                            if (chatTheme.separatorStyle == "solid") {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            } else if (chatTheme.separatorStyle == "dotted") {
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                                    repeat(10) { Text(".", color = Color.Gray, modifier = Modifier.padding(horizontal = 2.dp)) }
                                }
                            }
                        }
                    }
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
                            if (chatTheme.inputBarStyle == "filled") {
                                TextField(
                                    value = messageText,
                                    onValueChange = { 
                                        messageText = it
                                        onTyping(it.isNotBlank())
                                    },
                                    placeholder = { Text(stringResource(com.eventos.banana.R.string.messages_placeholder)) },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 3,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = (activeColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.1f),
                                        unfocusedContainerColor = (activeColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.1f),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = activeColor ?: MaterialTheme.colorScheme.primary
                                    )
                                )
                            } else {
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
                                        focusedBorderColor = if (chatTheme.inputBarStyle == "invisible") Color.Transparent else (activeColor ?: MaterialTheme.colorScheme.primary),
                                        unfocusedBorderColor = if (chatTheme.inputBarStyle == "invisible") Color.Transparent else MaterialTheme.colorScheme.outline,
                                        cursorColor = activeColor ?: MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            
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
