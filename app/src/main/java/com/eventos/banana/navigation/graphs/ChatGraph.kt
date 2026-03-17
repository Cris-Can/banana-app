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

fun NavGraphBuilder.chatGraph(
    navController: NavController,
    sessionViewModel: SessionViewModel
) {
    // ---------- CONVERSATIONS ----------
    composable(Screen.Conversations.route) {
        val conversationsViewModel: ConversationsViewModel = hiltViewModel()
        val currentUserId = sessionViewModel.currentUserId() ?: ""
        val conversationsFlow = remember(currentUserId) { conversationsViewModel.observeConversations(currentUserId) }
        val conversations by conversationsFlow.collectAsState(initial = emptyList())
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
        LaunchedEffect(conversationId) {
            messageRepository.markConversationAsRead(conversationId, currentUserId)
        }
        var messageLimit by remember { mutableIntStateOf(30) }
        val messages by messageRepository.observeMessages(conversationId, messageLimit)
            .collectAsState(initial = emptyList())
        val conversationDetails by messageRepository.observeConversation(conversationId).collectAsState(initial = null)
        val themeColor = conversationDetails?.themeColor
        val profileUiState by sessionViewModel.profileUiState.collectAsState()
        val isGold = profileUiState.profile?.isGold == true
        val otherNickname = (conversationDetails?.participantNicknames?.get(
            conversationDetails?.participants?.firstOrNull { it != currentUserId }
        )) ?: "Chat"
        val otherUserId = conversationDetails?.participants?.firstOrNull { it != currentUserId }
        val scope = rememberCoroutineScope()
        ChatScreen(
            viewModel = chatViewModel,
            otherUserNickname = otherNickname,
            messages = messages,
            currentUserId = currentUserId,
            themeColor = themeColor,
            isGold = isGold,
            otherUserIsTyping = conversationDetails?.typingUsers?.any { it != currentUserId } == true,
            onSendMessage = { content, rId -> chatViewModel.sendMessage(content, rId) },
            onSendAudio = { audioBytes, durationMs, replyId -> chatViewModel.sendAudio(audioBytes, durationMs, replyId) },
            onTyping = { isTyping -> chatViewModel.setTypingStatus(isTyping) },
            onUpdateTheme = { color ->
                scope.launch { messageRepository.updateConversationTheme(conversationId, color) }
            },
            onBack = { navController.popBackStack() },
            onReportUser = { reason -> /* logic handled in screen/vm */ },
            onBlockUser = { /* logic handled in screen/vm */ },
            onDeleteMessage = { msgId ->
                scope.launch { messageRepository.deleteMessage(conversationId, msgId) }
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
    }

    // ---------- START CHAT (from profile) ----------
    composable(
        route = Screen.StartChat.routePattern,
        arguments = listOf(navArgument("targetUserId") { type = NavType.StringType })
    ) { backStackEntry ->
        val targetUserId = backStackEntry.arguments?.getString("targetUserId") ?: return@composable
        val convViewModel: ConversationsViewModel = hiltViewModel()
        val userViewModel: UserViewModel = hiltViewModel()
        val messageRepository = convViewModel.messageRepository
        val currentUserId = sessionViewModel.currentUserId()
        val profileUiState by sessionViewModel.profileUiState.collectAsState()
        val currentNickname = profileUiState.profile?.nickname ?: "Usuario"

        LaunchedEffect(Unit) {
            val userRepository = userViewModel.userRepository
            val targetProfile = userRepository.getUserProfile(targetUserId)
            val otherNickname = targetProfile?.nickname ?: "Usuario"
            val result = messageRepository.getOrCreateConversation(
                currentUserId = currentUserId,
                otherUserId = targetUserId,
                currentUserNickname = currentNickname,
                otherUserNickname = otherNickname
            )
            result.onSuccess { conversationId ->
                navController.navigate("chat/$conversationId") {
                    popUpTo("start_chat/$targetUserId") { inclusive = true }
                }
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
