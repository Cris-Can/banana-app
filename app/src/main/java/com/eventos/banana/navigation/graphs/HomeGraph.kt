package com.eventos.banana.navigation.graphs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.hilt.navigation.compose.*
import androidx.compose.animation.*
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.eventos.banana.ui.auth.SessionViewModel
import com.eventos.banana.ui.home.HomeScreen
import com.eventos.banana.ui.notifications.NotificationViewModel
import com.eventos.banana.ui.notifications.NotificationsScreen
import com.eventos.banana.ui.messages.ConversationsViewModel
import com.eventos.banana.ui.maps.WorldMapScreen
import com.eventos.banana.ui.search.UnifiedSearchScreen
import com.eventos.banana.domain.model.NotificationType
import com.eventos.banana.navigation.Screen

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.homeGraph(
    navController: NavController,
    sessionViewModel: SessionViewModel,
    notificationViewModel: NotificationViewModel,
    conversationsViewModel: ConversationsViewModel,
    sharedTransitionScope: SharedTransitionScope
) {
    composable(Screen.Home.route) {
        val currentId = sessionViewModel.currentUserId() ?: ""



        LaunchedEffect(currentId) {
            notificationViewModel.start(currentId)
            conversationsViewModel.startUnreadMessagesObservation(currentId)
        }
        
        val unreadCount by notificationViewModel.unreadCount.collectAsState()
        val unreadMessagesCount by conversationsViewModel.unreadMessagesCount.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {
            HomeScreen(
                sessionViewModel = sessionViewModel,
                unreadNotifications = unreadCount,
                unreadMessages = unreadMessagesCount,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
                onCreateEventClick = { navController.navigate(Screen.CreateEvent.route) },
                onEventClick = { eventId -> navController.navigate(Screen.EventDetail(eventId).route) },
                onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onFriendsClick = { navController.navigate(Screen.Friends().route) },
                onMessagesClick = { navController.navigate(Screen.Conversations.route) },
                onMapClick = { navController.navigate(Screen.WorldMap.route) }
            )

        }
    }

    composable(Screen.WorldMap.route) {
        val profileUiState by sessionViewModel.profileUiState.collectAsState()
        WorldMapScreen(
            currentUserId = sessionViewModel.currentUserId() ?: "",
            isIdentityVerified = profileUiState.profile?.identityVerified ?: false,
            onBack = { navController.popBackStack() },
            onEventClick = { eventId -> navController.navigate("event_detail/$eventId") }
        )
    }

    composable(Screen.Notifications.route) {
        val notificationViewModel: NotificationViewModel = hiltViewModel()
        val userId = sessionViewModel.currentUserId()
        LaunchedEffect(Unit) {
            notificationViewModel.start(userId)
            notificationViewModel.markAllAsRead(userId)
        }
        val notifications by notificationViewModel.notifications.collectAsState()
        NotificationsScreen(
            notifications = notifications,
            onBack = { navController.popBackStack() },
            onNotificationClick = { notification ->
                notificationViewModel.markAllAsRead(userId)
                when (notification.type) {
                    NotificationType.FRIEND_REQUEST -> navController.navigate(Screen.Friends().route)
                    NotificationType.NEW_MESSAGE -> notification.eventId?.let { id -> navController.navigate(Screen.Chat(id).route) }
                    NotificationType.FRIEND_ACCEPTED -> notification.fromUserId?.let { id -> navController.navigate(Screen.PublicProfile(id).route) }
                    else -> notification.eventId?.let { id -> navController.navigate(Screen.EventDetail(id).route) }
                }
            }
        )
    }

    composable(Screen.Search.route) {
        UnifiedSearchScreen(
            navController = navController,
            currentUserId = sessionViewModel.currentUserId() ?: ""
        )
    }
}
