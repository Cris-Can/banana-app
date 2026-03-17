package com.eventos.banana.navigation.graphs

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.hilt.navigation.compose.*
import com.eventos.banana.ui.auth.SessionViewModel
import com.eventos.banana.ui.monetization.BillingViewModel
import com.eventos.banana.ui.monetization.BananaGoldScreen
import com.eventos.banana.ui.monetization.AppIconSelectorScreen
import com.eventos.banana.navigation.Screen

fun NavGraphBuilder.monetizationGraph(
    navController: NavController,
    sessionViewModel: SessionViewModel
) {
    composable(Screen.Gold.route) {
        val billingViewModel: BillingViewModel = hiltViewModel()
        BananaGoldScreen(
            billingViewModel = billingViewModel,
            onDismiss = { navController.popBackStack() },
            onNavigateToIcons = { navController.navigate("app_icons") }
        )
    }

    composable(Screen.AppIcons.route) {
        AppIconSelectorScreen(
            onBack = { navController.popBackStack() }
        )
    }
}
