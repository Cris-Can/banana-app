package com.eventos.banana.navigation

import androidx.compose.runtime.*



import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventos.banana.ui.event.CreateEventScreen
import com.eventos.banana.ui.home.HomeScreen
import com.eventos.banana.ui.login.LoginScreen
import com.eventos.banana.ui.splash.SplashScreen
import com.eventos.banana.viewmodel.SessionViewModel
import com.eventos.banana.viewmodel.EventViewModel
import com.eventos.banana.viewmodel.CreateEventViewModel
import com.eventos.banana.domain.model.SessionState
import androidx.compose.runtime.collectAsState




@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = viewModel()
    val sessionState by sessionViewModel.sessionState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "splash"

    )

    {

        composable("splash") {
            SplashScreen()
        }

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


        composable("login") {
            val loginUiState by sessionViewModel.loginUiState.collectAsState()

            LoginScreen(
                uiState = loginUiState,
                onLogin = { email, password ->
                    sessionViewModel.login(email, password)
                }
            )
        }


        composable("home") {
            val profileUiState by sessionViewModel.profileUiState.collectAsState()

            HomeScreen(
                profileUiState = profileUiState,
                onLogout = {
                    sessionViewModel.logout()
                },
                onCreateEvent = {
                    navController.navigate("create_event")
                    }
            )
        }
        composable("createEvent") {
            val createEventViewModel: CreateEventViewModel = viewModel()
            val uiState by createEventViewModel.uiState.collectAsState()

            CreateEventScreen(
                creatorId = sessionViewModel.currentUserId(), // o el uid que ya uses
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




    }

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
