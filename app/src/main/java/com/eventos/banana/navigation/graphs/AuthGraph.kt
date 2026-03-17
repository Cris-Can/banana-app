package com.eventos.banana.navigation.graphs

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.compose.animation.*
import com.eventos.banana.ui.auth.SessionViewModel
import com.eventos.banana.ui.splash.SplashScreen
import com.eventos.banana.ui.login.LoginScreen
import com.eventos.banana.ui.login.EmailVerificationScreen
import com.eventos.banana.navigation.Screen

fun NavGraphBuilder.authGraph(
    navController: NavController,
    sessionViewModel: SessionViewModel,
    sharedPreferences: SharedPreferences,
    hasSeenOnboarding: MutableState<Boolean>
) {
    composable(Screen.Splash.route) {
        SplashScreen()
    }

    composable(Screen.Onboarding.route) {
        com.eventos.banana.ui.onboarding.OnboardingScreen(
            onFinish = {
                sharedPreferences.edit().putBoolean("onboarding_seen_v2", true).apply()
                hasSeenOnboarding.value = true
                navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                }
            }
        )
    }

    composable(
        route = Screen.Login.route,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
    ) {
        val loginUiState by sessionViewModel.loginUiState.collectAsState()
        val registerUiState by sessionViewModel.registerUiState.collectAsState()
        LoginScreen(
            loginUiState = loginUiState,
            registerUiState = registerUiState,
            onLogin = sessionViewModel::login,
            onRegister = sessionViewModel::register,
            onForgotPassword = sessionViewModel::resetPassword
        )
    }

    composable(Screen.Verification.route) {
        EmailVerificationScreen(
            sessionViewModel = sessionViewModel,
            onVerified = {
                navController.navigate("home") {
                    popUpTo("verification") { inclusive = true }
                }
            },
            onLogout = {
                sessionViewModel.logout()
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }
}
