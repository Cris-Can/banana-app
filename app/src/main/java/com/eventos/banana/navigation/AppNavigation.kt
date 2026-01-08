package com.eventos.banana.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eventos.banana.domain.model.SessionState
import com.eventos.banana.ui.event.CreateEventScreen
import com.eventos.banana.ui.event.EventDetailRoute
import com.eventos.banana.ui.home.HomeScreen
import com.eventos.banana.ui.login.LoginScreen
import com.eventos.banana.ui.splash.SplashScreen
import com.eventos.banana.viewmodel.CreateEventViewModel
import com.eventos.banana.viewmodel.EventDetailViewModel
import com.eventos.banana.viewmodel.SessionViewModel
import kotlinx.coroutines.flow.collectLatest

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
                onLogin = { email, password ->
                    sessionViewModel.login(email, password)
                }
            )
        }

        // ---------- HOME ----------
        composable("home") {
            HomeScreen(
                sessionViewModel = sessionViewModel,
                onCreateEventClick = {
                    navController.navigate("create_event")
                },
                onEventClick = { eventId ->
                    navController.navigate("event_detail/$eventId")
                }
            )
        }

        // ---------- CREATE EVENT ----------
        composable("create_event") {
            val createEventViewModel: CreateEventViewModel = viewModel()
            val uiState by createEventViewModel.uiState.collectAsState()

            CreateEventScreen(
                creatorId = sessionViewModel.currentUserId(),
                uiState = uiState,
                onCreateEvent = { event ->
                    createEventViewModel.createEvent(event)
                },
                onSuccess = {
                    createEventViewModel.resetState()
                    navController.popBackStack()
                }
            )
        }

        // ---------- EVENT DETAIL ----------
        composable(
            route = "event_detail/{eventId}",
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType }
            )
        ) { backStackEntry ->

            val eventId = backStackEntry.arguments?.getString("eventId")
                ?: return@composable

            val eventDetailViewModel: EventDetailViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(
                        modelClass: Class<T>
                    ): T {
                        return EventDetailViewModel(eventId) as T
                    }
                }
            )

            val uiState by eventDetailViewModel.uiState.collectAsState()

            EventDetailRoute(
                uiState = uiState,
                currentUserId = sessionViewModel.currentUserId(),
                onJoinClick = {
                    eventDetailViewModel.requestJoinEvent(
                        sessionViewModel.currentUserId()
                    )
                },
                onApproveClick = { userId ->
                    eventDetailViewModel.approveParticipant(userId)
                },
                onRejectClick = { userId ->
                    eventDetailViewModel.rejectParticipant(userId)
                }
            )

        }
    }

    // ---------- SESSION REDIRECTION ----------
    LaunchedEffect(sessionState) {
        when (sessionState) {
            SessionState.LOADING -> Unit

            SessionState.NOT_AUTHENTICATED -> {
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }

            SessionState.AUTHENTICATED -> {
                navController.navigate("home") {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }
}
