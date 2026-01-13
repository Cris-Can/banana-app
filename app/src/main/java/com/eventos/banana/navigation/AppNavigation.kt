package com.eventos.banana.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.eventos.banana.domain.model.EventDetailUiState
import com.eventos.banana.domain.model.SessionState
import com.eventos.banana.ui.event.*
import com.eventos.banana.ui.home.HomeScreen
import com.eventos.banana.ui.login.LoginScreen
import com.eventos.banana.ui.notifications.NotificationsScreen
import com.eventos.banana.ui.profile.ProfileScreen
import com.eventos.banana.ui.splash.SplashScreen
import com.eventos.banana.viewmodel.*

@Composable
fun AppNavigation() {

    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = viewModel()
    val sessionState by sessionViewModel.sessionState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {

        // ---------- SPLASH ----------
        composable("splash") {
            SplashScreen()
        }

        // ---------- LOGIN ----------
        composable("login") {
            val loginUiState by sessionViewModel.loginUiState.collectAsState()

            LoginScreen(
                uiState = loginUiState,
                onLogin = sessionViewModel::login
            )
        }

        // ---------- HOME ----------
        composable("home") {

            val notificationViewModel: NotificationViewModel = viewModel()

            LaunchedEffect(Unit) {
                notificationViewModel.start(sessionViewModel.currentUserId())
            }

            val notifications by notificationViewModel.notifications.collectAsState()
            val unreadCount = notifications.count { !it.read }

            HomeScreen(
                sessionViewModel = sessionViewModel,
                unreadNotifications = unreadCount,
                onCreateEventClick = {
                    navController.navigate("create_event")
                },
                onEventClick = { eventId ->
                    navController.navigate("event_detail/$eventId")
                },
                onNotificationsClick = {
                    navController.navigate("notifications")
                },
                onProfileClick = {
                    navController.navigate("profile")
                }
            )
        }

        // ---------- CREATE EVENT ----------
        composable("create_event") {
            val vm: CreateEventViewModel = viewModel()
            val uiState by vm.uiState.collectAsState()

            CreateEventScreen(
                creatorId = sessionViewModel.currentUserId(),
                uiState = uiState,
                onCreateEvent = vm::createEvent,
                onSuccess = {
                    vm.resetState()
                    navController.popBackStack()
                }
            )
        }

        // ---------- EVENT DETAIL ----------
        composable(
            "event_detail/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->

            val eventId =
                backStackEntry.arguments?.getString("eventId") ?: return@composable

            val vm: EventDetailViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return EventDetailViewModel(eventId) as T
                    }
                }
            )

            val uiState by vm.uiState.collectAsState()

            EventDetailRoute(
                uiState = uiState,
                currentUserId = sessionViewModel.currentUserId(),
                onJoinClick = {
                    navController.navigate("questionnaire/$eventId")
                },
                onApproveClick = vm::approveParticipant,
                onRejectClick = vm::rejectParticipant,
                onCancelEvent = { reason ->
                    vm.cancelEvent(reason)
                    navController.popBackStack("home", inclusive = false)
                },
                onCloseEvent = {
                    vm.closeEvent()
                    navController.popBackStack("home", inclusive = false)
                },
                onRemoveParticipant = { userId ->
                    vm.removeParticipant(userId)
                }
            )


        }

        // ---------- QUESTIONNAIRE ----------
        composable(
            "questionnaire/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->

            val eventId =
                backStackEntry.arguments?.getString("eventId") ?: return@composable

            val vm: EventDetailViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return EventDetailViewModel(eventId) as T
                    }
                }
            )

            val uiState by vm.uiState.collectAsState()

            if (uiState is EventDetailUiState.Success) {
                QuestionnaireScreen(
                    event = (uiState as EventDetailUiState.Success).event,
                    onSubmit = { answers ->
                        vm.requestJoinEventWithAnswers(
                            userId = sessionViewModel.currentUserId(),
                            answers = answers
                        )
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
        }

        // ---------- NOTIFICATIONS ----------
        composable("notifications") {

            val notificationViewModel: NotificationViewModel = viewModel()
            val userId = sessionViewModel.currentUserId()

            LaunchedEffect(Unit) {
                notificationViewModel.start(userId)
                notificationViewModel.markAllAsRead(userId)
            }

            val notifications by notificationViewModel.notifications.collectAsState()

            NotificationsScreen(
                notifications = notifications,
                onBack = { navController.popBackStack() },
                onNotificationClick = { eventId ->
                    notificationViewModel.markAllAsRead(userId)
                    navController.navigate("event_detail/$eventId")
                }
            )
        }

        // ---------- PROFILE ----------
        composable("profile") {
            ProfileScreen(
                sessionViewModel = sessionViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }

    // ---------- SESSION REDIRECTION ----------
    LaunchedEffect(sessionState) {
        when (sessionState) {
            SessionState.LOADING -> Unit
            SessionState.NOT_AUTHENTICATED ->
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }

            SessionState.AUTHENTICATED ->
                navController.navigate("home") {
                    popUpTo(0) { inclusive = true }
                }
        }
    }
}
