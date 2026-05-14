package com.eventos.banana.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import com.eventos.banana.domain.model.EventDetailUiState
import com.eventos.banana.domain.model.ExactLocation
import com.eventos.banana.domain.model.SessionState
import com.eventos.banana.ui.auth.*
import com.eventos.banana.ui.event.*
import com.eventos.banana.ui.home.*
import com.eventos.banana.ui.messages.*
import com.eventos.banana.ui.monetization.*
import com.eventos.banana.ui.notifications.*
import com.eventos.banana.ui.onboarding.*
import com.eventos.banana.ui.profile.*
import com.eventos.banana.ui.rating.*
import com.eventos.banana.navigation.graphs.*

@Composable
fun AppNavigation(startDestination: String = "splash") {

    val navController = rememberNavController()
    
    // Check if we have a session to decide real start if not splash
    // ... logic simplifies to: checking sessionViewModel state later

    // ... (rest of vals)

    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("banana_prefs", android.content.Context.MODE_PRIVATE)
    }
    
    // ---------- SESSION VIEW MODEL (HILT) ----------
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val sessionState by sessionViewModel.sessionState.collectAsState()

    // 🔒 Cache onboarding state to avoid flash on each session state change
    val hasSeenOnboarding = remember {
        androidx.compose.runtime.mutableStateOf(
            sharedPreferences.getBoolean("onboarding_seen_v2", false)
        )
    }



    // Logic: Start at Splash.
    // If Authenticated -> Check Onboarding -> Home/Onboarding
    // If Not Authenticated -> Login

    Box(modifier = Modifier.fillMaxSize()) {
        @OptIn(ExperimentalSharedTransitionApi::class)
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
        
                // ---------- AUTH GRAPH ----------
                authGraph(
                    navController = navController,
                    sessionViewModel = sessionViewModel,
                    sharedPreferences = sharedPreferences,
                    hasSeenOnboarding = hasSeenOnboarding
                )
        
                // ---------- MONETIZATION GRAPH ----------
                monetizationGraph(
                    navController = navController,
                    sessionViewModel = sessionViewModel
                )

                // ---------- HOME GRAPH ----------
                homeGraph(
                    navController = navController,
                    sessionViewModel = sessionViewModel,
                    sharedTransitionScope = this@SharedTransitionLayout
                )

                // ---------- EVENT GRAPH ----------
                eventGraph(
                    navController = navController,
                    sessionViewModel = sessionViewModel,
                    sharedTransitionScope = this@SharedTransitionLayout
                )

                // ---------- PROFILE GRAPH ----------
                profileGraph(
                    navController = navController,
                    sessionViewModel = sessionViewModel,
                    sharedPreferences = sharedPreferences
                )

                // ---------- CHAT GRAPH ----------
                chatGraph(
                    navController = navController,
                    sessionViewModel = sessionViewModel
                )

                
        }
        
    } // End SharedTransitionLayout
    } // End Box

    // ---------- SESSION REDIRECTION ----------
    val navigator = remember(navController) { Navigator(navController) }
    
    LaunchedEffect(sessionState, sessionViewModel.isVerificationChecked) {
        navigator.handleSessionRedirection(
            sessionState = sessionState,
            isVerificationChecked = sessionViewModel.isVerificationChecked,
            isEmailVerified = sessionViewModel.isEmailVerified,
            hasSeenOnboarding = hasSeenOnboarding.value,
            startDestination = startDestination
        )
    }
}
