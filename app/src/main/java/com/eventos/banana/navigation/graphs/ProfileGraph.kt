package com.eventos.banana.navigation.graphs

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.hilt.navigation.compose.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.eventos.banana.ui.auth.SessionViewModel
import com.eventos.banana.ui.profile.*
import com.eventos.banana.ui.settings.*
import com.eventos.banana.ui.screens.*
import com.eventos.banana.ui.ranking.RankingScreen
import com.eventos.banana.ui.ranking.RankingViewModel
import com.eventos.banana.navigation.Screen

fun NavGraphBuilder.profileGraph(
    navController: NavController,
    sessionViewModel: SessionViewModel,
    sharedPreferences: android.content.SharedPreferences
) {
    // ---------- LEADERBOARD (RANKING) ----------
    composable(Screen.Leaderboard.route) {
        val rankingViewModel: RankingViewModel = hiltViewModel()
        RankingScreen(
            viewModel = rankingViewModel,
            onBack = { navController.popBackStack() },
            onUserClick = { uid -> navController.navigate(Screen.PublicProfile(uid).route) }
        )
    }

    // ---------- PROFILE ----------
    composable(Screen.Profile.route) {
        ProfileScreen(
            sessionViewModel = sessionViewModel,
            onBack = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                }
            },
            onFriendsClick = { navController.navigate(Screen.Friends().route) },
            onEventClick = { eventId -> navController.navigate(Screen.EventDetail(eventId).route) },
            onSettingsClick = { navController.navigate(Screen.Settings.route) },
            onProfileViewsClick = {
                navController.navigate(Screen.ProfileViews(sessionViewModel.currentUserId() ?: "").route)
            },
            onLeaderboardClick = { navController.navigate(Screen.Leaderboard.route) },
            onRatingsClick = { userId -> navController.navigate(Screen.UserRatings(userId).route) }
        )
    }

    // ---------- USER RATINGS ----------
    composable(
        route = Screen.UserRatings.routePattern,
        arguments = listOf(navArgument("userId") { type = NavType.StringType })
    ) { backStackEntry ->
        val targetUserId = backStackEntry.arguments?.getString("userId") ?: return@composable
        val profileUiState by sessionViewModel.profileUiState.collectAsState()
        val isGold = profileUiState.profile?.isGold == true
        val vm: UserRatingsViewModel = hiltViewModel<UserRatingsViewModel, UserRatingsViewModel.Factory>(
            creationCallback = { factory -> factory.create(targetUserId, isGold) }
        )
        UserRatingsScreen(
            viewModel = vm,
            onBack = { navController.popBackStack() }
        )
    }

    // ---------- PROFILE VIEWS ----------
    composable(
        route = Screen.ProfileViews.routePattern,
        arguments = listOf(navArgument("userId") { type = NavType.StringType })
    ) { backStackEntry ->
        val targetUserId = backStackEntry.arguments?.getString("userId") ?: return@composable
        val profileViewModel: ProfileViewModel = hiltViewModel()
        val profileUiState by sessionViewModel.profileUiState.collectAsState()
        val isGold = profileUiState.profile?.isGold == true
        ProfileViewsScreen(
            profileViewModel = profileViewModel,
            currentUserUid = sessionViewModel.currentUserId() ?: "",
            isGold = isGold,
            onBack = { navController.popBackStack() },
            onNavigateToGold = { navController.navigate("gold") },
            onUserClick = { userId -> navController.navigate("public_profile/$userId") }
        )
    }

    // ---------- SETTINGS ----------
    composable(Screen.Settings.route) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val deleteStatus by sessionViewModel.deleteAccountStatus.collectAsState()
        val profileViewModel: ProfileViewModel = hiltViewModel()
        val profileUiState by sessionViewModel.profileUiState.collectAsState()
        val profileViewModelCallbackUiState by profileViewModel.uiState.collectAsState()
        
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToGold = { navController.navigate(Screen.Gold.route) },
            onNavigateToAdmin = { navController.navigate(Screen.AdminDashboard.route) },
            onLogout = { 
                sessionViewModel.logout()
                navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
            },
            onDeleteAccount = { sessionViewModel.deleteAccount() },
            deleteAccountStatus = deleteStatus,
            onResetDeleteStatus = { sessionViewModel.resetDeleteAccountStatus() },
            onGuideReset = {
                sharedPreferences.edit().putBoolean("onboarding_seen_v2", false).apply()
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
            },
            userProfile = profileUiState.profile,
            onUpdateTheme = { theme -> 
                profileUiState.profile?.uid?.let { uid -> profileViewModel.updateAppTheme(uid, theme) }
            },
            onSendPasswordReset = { email -> profileViewModel.sendPasswordReset(email) },
            onVerifyEmail = { sessionViewModel.sendEmailVerification() },
            isEmailVerified = sessionViewModel.isEmailVerified,
            onRecalculateStats = { uid -> profileViewModel.recalculateStats(uid) },
            onUpdateLocation = { uid, region, commune, lat, lng ->
                if (lat != null && lng != null) {
                    profileViewModel.updateLocationFromDevice(uid, region, commune, lat, lng)
                } else {
                    profileViewModel.updateLocation(uid, region, commune)
                }
            },
            onUpdateNotifyCommune = { enabled, region, commune ->
                profileUiState.profile?.uid?.let { uid ->
                    profileViewModel.updateNotifyEventsByCommune(uid, enabled, region, commune)
                }
            },
            onToggleCategorySubscription = { topic, isEnabled ->
                profileUiState.profile?.uid?.let { uid ->
                    profileViewModel.toggleCategorySubscription(uid, topic, isEnabled)
                }
            },
            onUpdateNotifyWall = { enabled ->
                sessionViewModel.currentUserId()?.let { uid ->
                    profileViewModel.updateNotifyEventWall(uid, enabled)
                }
            },
            onNavigateToIcons = { navController.navigate(Screen.AppIcons.route) },
            onNavigateToBlockedUsers = { navController.navigate(Screen.BlockedUsers.route) },
            onMigrateEvents = { profileViewModel.runMigration(context) },
            migrationStatus = profileViewModel.migrationStatus.collectAsState().value,
            profileUiState = profileViewModelCallbackUiState
        )
    }

    // ---------- BLOCKED USERS ----------
    composable(Screen.BlockedUsers.route) {
        BlockedUsersScreen(
            onBack = { navController.popBackStack() },
            onUserClick = { userId -> navController.navigate(Screen.PublicProfile(userId).route) }
        )
    }

    // ---------- ADMIN DASHBOARD ----------
    composable(Screen.AdminDashboard.route) {
        AdminDashboardScreen(
            onBack = { navController.popBackStack() },
            onNavigateToProfile = { userId -> navController.navigate(Screen.PublicProfile(userId).route) }
        )
    }

    // ---------- FRIENDS LIST ----------
    composable(
        route = Screen.Friends.routePattern,
        arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })
    ) { backStackEntry ->
        val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
        FriendListScreen(
            currentUserId = sessionViewModel.currentUserId(),
            onBack = { navController.popBackStack() },
            onUserClick = { userId -> navController.navigate(Screen.PublicProfile(userId).route) },
            initialTab = initialTab
        )
    }

    // ---------- PUBLIC PROFILE ----------
    composable(
        route = Screen.PublicProfile.routePattern,
        arguments = listOf(navArgument("userId") { type = NavType.StringType })
    ) { backStackEntry ->
        val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
        PublicProfileScreen(
            targetUserId = userId,
            onBack = { navController.popBackStack() },
            isCurrentUserVerified = sessionViewModel.isEmailVerified,
            onMessageClick = { targetUserId -> navController.navigate(Screen.StartChat(targetUserId).route) }
        )
    }
}
