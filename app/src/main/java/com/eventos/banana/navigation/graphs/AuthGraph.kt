package com.eventos.banana.navigation.graphs

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.compose.animation.*
import com.eventos.banana.ui.auth.SessionViewModel
import com.eventos.banana.ui.auth.ResetPasswordScreen
import com.eventos.banana.ui.splash.SplashScreen
import com.eventos.banana.ui.login.LoginScreen
import com.eventos.banana.ui.login.EmailVerificationScreen
import com.eventos.banana.navigation.Screen
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

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
        val hasBiometric by sessionViewModel.hasBiometricCredentials.collectAsState()
        val context = androidx.compose.ui.platform.LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var pendingEnableBiometric by remember { mutableStateOf(false) }
        
        LoginScreen(
            loginUiState = loginUiState,
            registerUiState = registerUiState,
            onLogin = { email, password ->
                sessionViewModel.login(email, password)
                if (pendingEnableBiometric) {
                    sessionViewModel.enableBiometricLogin(email, password)
                    pendingEnableBiometric = false
                }
            },
            onRegister = { email, pass, nick, birth, com, reg, country, lat, lng, invitationCode ->
                sessionViewModel.register(email, pass, nick, birth, com, reg, country, lat, lng, invitationCode)
            },
            onForgotPassword = sessionViewModel::resetPassword,
            hasBiometricCredentials = hasBiometric,
            onBiometricLogin = {
                // Launch biometric prompt then auto-login
                val activity = context as? androidx.fragment.app.FragmentActivity
                if (activity != null) {
                    val helper = com.eventos.banana.util.BiometricHelper(
                        activity = activity,
                        onAuthSuccess = {
                            sessionViewModel.loginWithBiometrics()
                        },
                        onAuthError = { error ->
                            // Error handled by ViewModel state
                        }
                    )
                    if (helper.canAuthenticate()) {
                        helper.authenticate()
                    }
                }
            },
            onEnableBiometric = {
                pendingEnableBiometric = true
            }
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

    // ---- Password Reset (deep link) ----
    composable(
        route = Screen.ResetPassword.routePattern,
        arguments = listOf(
            navArgument("oobCode") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
        deepLinks = listOf(
            navDeepLink {
                uriPattern = "https://bananaapp-aa46e.web.app/reset-password?oobCode={oobCode}"
            }
        )
    ) { backStackEntry ->
        val oobCode = backStackEntry.arguments?.getString("oobCode") ?: ""
        ResetPasswordScreen(
            oobCode = oobCode,
            sessionViewModel = sessionViewModel,
            onNavigateToLogin = {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }
}
