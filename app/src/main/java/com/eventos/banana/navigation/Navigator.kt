package com.eventos.banana.navigation

import androidx.navigation.NavController
import com.eventos.banana.domain.model.SessionState

class Navigator(private val navController: NavController) {

    fun handleSessionRedirection(
        sessionState: SessionState,
        isVerificationChecked: Boolean,
        isEmailVerified: Boolean,
        hasSeenOnboarding: Boolean,
        startDestination: String
    ) {
        when (sessionState) {
            SessionState.LOADING -> return

            SessionState.NOT_AUTHENTICATED -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }

            SessionState.AUTHENTICATED -> {
                if (!isVerificationChecked) return
                
                if (!isEmailVerified) {
                    navController.navigate(Screen.Verification.route) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    if (!hasSeenOnboarding) {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        val route = if (startDestination != Screen.Splash.route && startDestination != Screen.Home.route) {
                            startDestination
                        } else {
                            Screen.Home.route
                        }
                        navController.navigate(route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
        }
    }
}
