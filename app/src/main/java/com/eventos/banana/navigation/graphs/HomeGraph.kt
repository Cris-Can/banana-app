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
import com.eventos.banana.ui.onboarding.GuideViewModel
import com.eventos.banana.ui.home.HomeScreen
import com.eventos.banana.ui.home.HomeGuideOverlay
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
    guideViewModel: GuideViewModel,
    sharedTransitionScope: SharedTransitionScope
) {
    composable(Screen.Home.route) {
        val notificationViewModel: NotificationViewModel = hiltViewModel()
        val conversationsViewModel: ConversationsViewModel = hiltViewModel()
        val currentId = sessionViewModel.currentUserId() ?: ""

        // Controlar visibilidad del HomeGuide — se muestra solo la primera vez
        val context = androidx.compose.ui.platform.LocalContext.current
        val prefs = remember {
            context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        }
        var showGuide by androidx.compose.runtime.saveable.rememberSaveable {
            mutableStateOf(!prefs.getBoolean("home_guide_v1", false))
        }

        LaunchedEffect(currentId) {
            notificationViewModel.start(currentId)
            conversationsViewModel.startUnreadMessagesObservation(currentId)
            // Ya no iniciamos el GuideViewModel — el nuevo overlay lo reemplaza
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

            // 🎓 Tutorial unificado: flechas + card inferior sin bloquear la pantalla
            if (showGuide) {
                com.eventos.banana.ui.home.HomeGuideOverlay(
                    onDismiss = {
                        prefs.edit().putBoolean("home_guide_v1", true).commit() // commit() es síncrono para evitar loop en recomposición rápida
                        showGuide = false
                    }
                )
            }
        }
    }

    composable(Screen.WorldMap.route) {
        WorldMapScreen(
            currentUserId = sessionViewModel.currentUserId() ?: "",
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
