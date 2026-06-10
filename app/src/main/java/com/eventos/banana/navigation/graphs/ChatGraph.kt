package com.eventos.banana.navigation.graphs

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.hilt.navigation.compose.*
import androidx.compose.animation.*
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.eventos.banana.ui.auth.SessionViewModel
import com.eventos.banana.ui.messages.ConversationsViewModel
import com.eventos.banana.ui.messages.ChatViewModel
import com.eventos.banana.ui.messages.ChatScreen
import com.eventos.banana.ui.messages.ConversationsScreen
import com.eventos.banana.ui.profile.UserViewModel
import com.eventos.banana.navigation.Screen
import kotlinx.coroutines.launch
import com.eventos.banana.domain.model.ConversationTheme
import com.eventos.banana.domain.model.resolveTheme
import com.eventos.banana.ui.messages.ThemeConfigDialog

fun NavGraphBuilder.chatGraph(
    navController: NavController,
    sessionViewModel: SessionViewModel,
    conversationsViewModel: ConversationsViewModel
) {
    // ---------- CONVERSATIONS ----------
    composable(Screen.Conversations.route) {
        val currentUserId = sessionViewModel.currentUserId() ?: ""
        val conversationsFlow = remember(currentUserId) { conversationsViewModel.observeConversations(currentUserId) }
        val conversations by conversationsFlow.collectAsState(initial = emptyList())
        val userProfiles by conversationsViewModel.profiles.collectAsState()
        val profileUiState by sessionViewModel.profileUiState.collectAsState()
        val blockedUsers = profileUiState.profile?.blockedUsers ?: emptyList()
        val filteredConversations = remember(conversations, blockedUsers) {
            if (blockedUsers.isEmpty()) {
                conversations
            } else {
                conversations.filter { conv ->
                    val otherUserId = conv.participants.firstOrNull { it != currentUserId }
                    otherUserId == null || !blockedUsers.contains(otherUserId)
                }
            }
        }
        val scope = rememberCoroutineScope()
        ConversationsScreen(
            conversations = filteredConversations,
            currentUserId = currentUserId,
            userProfiles = userProfiles,
            onConversationClick = { conversationId -> navController.navigate("chat/$conversationId") },
            onDeleteConversation = { conversationId ->
                scope.launch { conversationsViewModel.deleteConversation(conversationId) }
            },
            onBack = { navController.popBackStack() }
        )
    }

    // ---------- CHAT ----------
    composable(
        route = Screen.Chat.routePattern,
        arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
    ) { backStackEntry ->
        val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
        val currentUserId = sessionViewModel.currentUserId() ?: ""
        val chatViewModel: ChatViewModel = hiltViewModel<ChatViewModel, ChatViewModel.Factory>(
            creationCallback = { factory -> factory.create(conversationId, currentUserId) }
        )
        val messageRepository = chatViewModel.repository
        val userViewModel: UserViewModel = hiltViewModel()
        val userRepository = userViewModel.userRepository
        val context = androidx.compose.ui.platform.LocalContext.current

        val activeConversationId by chatViewModel.activeConversationId.collectAsState()
        LaunchedEffect(activeConversationId) {
            if (activeConversationId != null && activeConversationId != conversationId) {
                navController.navigate("chat/$activeConversationId") {
                    popUpTo("chat/$conversationId") { inclusive = true }
                }
            }
        }

        LaunchedEffect(conversationId) {
            if (!conversationId.startsWith("usr_")) {
                messageRepository.markConversationAsRead(conversationId, currentUserId)
            }
        }
        var messageLimit by remember { mutableIntStateOf(30) }
        val messages by messageRepository.observeMessages(conversationId, messageLimit)
            .collectAsState(initial = emptyList())
        val conversationDetails by messageRepository.observeConversation(conversationId).collectAsState(initial = null)
        val themeColor = conversationDetails?.themeColor
        val chatTheme = remember(conversationDetails) { conversationDetails?.resolveTheme() ?: ConversationTheme() }
        var showThemeDialog by remember { mutableStateOf(false) }
        val profileUiState by sessionViewModel.profileUiState.collectAsState()
        val isPremium = profileUiState.profile?.isGold == true || profileUiState.profile?.isFounder == true

        var otherNickname by remember { mutableStateOf("Chat") }
        val otherUserId = remember(conversationId, conversationDetails) {
            if (conversationId.startsWith("usr_")) {
                conversationId.substringAfter("usr_")
            } else {
                conversationDetails?.participants?.firstOrNull { it != currentUserId }
            }
        }

        LaunchedEffect(conversationId, conversationDetails) {
            if (conversationId.startsWith("usr_")) {
                val targetUid = conversationId.substringAfter("usr_")
                val profile = userRepository.getUserProfile(targetUid)
                if (profile != null) {
                    otherNickname = profile.nickname ?: "Usuario"
                }
            } else {
                val details = conversationDetails
                if (details != null) {
                    val otherUid = details.participants.firstOrNull { it != currentUserId }
                    otherNickname = details.participantNicknames[otherUid] ?: "Chat"
                }
            }
        }

        val scope = rememberCoroutineScope()
        val onUpdateTheme = { theme: ConversationTheme ->
            scope.launch { messageRepository.updateConversationTheme(conversationId, theme) }
        }
        val onOpenThemeConfig = {
            if (isPremium) {
                showThemeDialog = true
            } else {
                android.widget.Toast.makeText(context, "🔒 Solo Gold/Founder — Hazte Gold o sé Founder para personalizar el chat", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        ChatScreen(
            viewModel = chatViewModel,
            otherUserNickname = otherNickname,
            messages = messages,
            currentUserId = currentUserId,
            chatTheme = chatTheme,
            isGold = isPremium,
            otherUserIsTyping = conversationDetails?.typingUsers?.any { it != currentUserId } == true,
            onSendMessage = { content, rId -> chatViewModel.sendMessage(content, rId) },
            onSendAudio = { audioBytes, durationMs, replyId -> chatViewModel.sendAudio(audioBytes, durationMs, replyId) },
            onTyping = { isTyping -> chatViewModel.setTypingStatus(isTyping) },
            onOpenThemeConfig = onOpenThemeConfig,
            onBack = { navController.popBackStack() },
            onReportUser = { reason ->
                if (otherUserId != null) {
                    scope.launch {
                        try {
                            userRepository.reportUser(currentUserId, otherUserId, reason)
                            android.widget.Toast.makeText(context, "✅ Usuario reportado", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "❌ Error al reportar: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onBlockUser = {
                if (otherUserId != null) {
                    scope.launch {
                        try {
                            userRepository.blockUser(currentUserId, otherUserId)
                            android.widget.Toast.makeText(context, "🚫 Usuario bloqueado", android.widget.Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "❌ Error al bloquear: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDeleteMessage = { msgId ->
                scope.launch { 
                    val result = messageRepository.deleteMessage(conversationId, msgId) 
                    result.onFailure { e ->
                        android.widget.Toast.makeText(context, "❌ Error al borrar mensaje: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        android.util.Log.e("ChatGraph", "Failed to delete message $msgId", e)
                    }.onSuccess {
                        android.widget.Toast.makeText(context, "✅ Mensaje borrado", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onEditMessage = { msgId, old, new ->
                scope.launch { messageRepository.editMessage(conversationId, msgId, old, new) }
            },
            onLoadMore = { messageLimit += 30 },
            onProfileClick = {
                if (otherUserId != null) {
                    navController.navigate(Screen.PublicProfile(otherUserId).route)
                }
            }
        )

        if (showThemeDialog) {
            ThemeConfigDialog(
                currentTheme = chatTheme,
                onSave = { newTheme ->
                    scope.launch { messageRepository.updateConversationTheme(conversationId, newTheme) }
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false }
            )
        }
    }

    // ---------- START CHAT (from profile) ----------
    composable(
        route = Screen.StartChat.routePattern,
        arguments = listOf(navArgument("targetUserId") { type = NavType.StringType })
    ) { backStackEntry ->
        val targetUserId = backStackEntry.arguments?.getString("targetUserId") ?: return@composable

        LaunchedEffect(targetUserId) {
            navController.navigate("chat/usr_$targetUserId") {
                popUpTo("start_chat/$targetUserId") { inclusive = true }
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
