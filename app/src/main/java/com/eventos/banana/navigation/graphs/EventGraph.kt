package com.eventos.banana.navigation.graphs

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.hilt.navigation.compose.*
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventos.banana.ui.auth.SessionViewModel
import com.eventos.banana.ui.event.*
import com.eventos.banana.ui.maps.MapLocationPickerScreen
import com.eventos.banana.ui.monetization.BillingViewModel
import com.eventos.banana.ui.rating.*
import com.eventos.banana.domain.model.ExactLocation
import com.eventos.banana.domain.model.EventDetailUiState
import com.eventos.banana.navigation.Screen

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.eventGraph(
    navController: NavController,
    sessionViewModel: SessionViewModel,
    sharedTransitionScope: SharedTransitionScope
) {
    // ---------- CREATE EVENT ----------
    composable(Screen.CreateEvent.route) { backStackEntry ->
        val vm: CreateEventViewModel = hiltViewModel()
        val exactLocation = backStackEntry.savedStateHandle.get<ExactLocation>("location_result")
        LaunchedEffect(exactLocation) {
            if (exactLocation != null) {
                vm.updateExactLocation(exactLocation)
                backStackEntry.savedStateHandle.remove<ExactLocation>("location_result")
            }
        }
        val formState by vm.formState.collectAsState()
        val profileUiState by sessionViewModel.profileUiState.collectAsState()
        CreateEventScreen(
            creatorId = sessionViewModel.currentUserId(),
            isIdentityVerified = profileUiState.profile?.identityVerified ?: false,
            viewModel = vm,
            onSelectExactLocation = {
                val route = if (formState.currentLatitude != null && formState.currentLongitude != null) {
                    Screen.PickLocation(formState.currentLatitude, formState.currentLongitude).route
                } else if (formState.exactLocation?.latitude != null) {
                    Screen.PickLocation(formState.exactLocation?.latitude, formState.exactLocation?.longitude).route
                } else {
                    Screen.PickLocation().route
                }
                navController.navigate(route)
            },
            onSuccess = {
                vm.resetState()
                navController.popBackStack()
            },
            onNavigateToPremium = {
                navController.navigate(Screen.Gold.route)
            }
        )
    }

    // ---------- MAP LOCATION PICKER ----------
    composable(
        route = Screen.PickLocation.routePattern,
        arguments = listOf(
            navArgument("lat") { type = NavType.StringType; nullable = true },
            navArgument("lng") { type = NavType.StringType; nullable = true }
        )
    ) { backStackEntry ->
        val latStr = backStackEntry.arguments?.getString("lat")
        val lngStr = backStackEntry.arguments?.getString("lng")
        val initialLat = latStr?.toDoubleOrNull()
        val initialLng = lngStr?.toDoubleOrNull()
        MapLocationPickerScreen(
            initialLatitude = initialLat,
            initialLongitude = initialLng,
            onLocationSelected = { lat: Double, lng: Double, addr: String, commune: String, region: String, country: String ->
                navController.previousBackStackEntry?.savedStateHandle?.set(
                    "location_result",
                    ExactLocation(lat, lng, addr, commune, region, country)
                )
                navController.popBackStack()
            },
            onBack = { navController.popBackStack() }
        )
    }

    // ---------- EVENT DETAIL ----------
    composable(
        route = Screen.EventDetail.routePattern,
        arguments = listOf(
            navArgument("eventId") { type = NavType.StringType },
            navArgument("tab") { type = NavType.IntType; defaultValue = 0 }
        ),
        enterTransition = { slideInVertically(initialOffsetY = { height -> height }) + fadeIn() },
        exitTransition = { slideOutVertically(targetOffsetY = { height -> height }) + fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically(targetOffsetY = { height -> height }) + fadeOut() },
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://bananaapp-aa46e.web.app/event/{eventId}" }
        )
    ) { backStackEntry ->
        val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
        val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
        val vm: EventDetailViewModel = hiltViewModel<EventDetailViewModel, EventDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(eventId) }
        )
        val billingViewModel: BillingViewModel = hiltViewModel()
        val profileUiState by sessionViewModel.profileUiState.collectAsState()
        val credits = profileUiState.profile?.ratingCredits ?: 0
        val uiState by vm.uiState.collectAsState()
        val isSaved by vm.isSaved.collectAsState()
        val hasAttended by vm.hasAttended.collectAsState()
        val checkInState by vm.checkInState.collectAsState()
        val actionState by vm.actionState.collectAsState()
        val submissionState by vm.joinSubmissionState.collectAsState()
        val adUnlockState by vm.adUnlockState.collectAsState()
        val newRequestAlert by vm.newRequestAlert.collectAsState()

        val context = androidx.compose.ui.platform.LocalContext.current

        // 🔔 Notificar al creador cuando llega una solicitud en tiempo real
        LaunchedEffect(newRequestAlert) {
            val alert = newRequestAlert
            if (!alert.isNullOrBlank()) {
                android.widget.Toast.makeText(context, "🔔 $alert", android.widget.Toast.LENGTH_SHORT).show()
                vm.resetNewRequestAlert()
            }
        }

        LaunchedEffect(vm, sessionViewModel.currentUserId()) {
            vm.loadUserInteractionState(sessionViewModel.currentUserId())
        }
        
        EventDetailRoute(
            uiState = uiState,
            currentUserId = sessionViewModel.currentUserId(),
            isEmailVerified = sessionViewModel.isEmailVerified,
            initialTab = initialTab,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onJoinClick = { navController.navigate(Screen.Questionnaire(eventId).route) },
            onApproveClick = vm::approveParticipant,
            onRejectClick = vm::rejectParticipant,
            onCancelEvent = { reason ->
                vm.cancelEvent(reason)
                navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = false } }
            },
            onCloseEvent = {
                vm.closeEvent()
                navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = false } }
            },
            onDeleteEvent = {
                vm.deleteEvent()
                navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } }
            },
            onRemoveParticipant = vm::removeParticipant,
            onRateUser = { targetUserId -> navController.navigate(Screen.RateUser(eventId, targetUserId).route) },
            onUserClick = { targetUserId ->
                if (targetUserId == sessionViewModel.currentUserId()) {
                    navController.navigate("profile")
                } else {
                    navController.navigate("public_profile/$targetUserId")
                }
            },
            onRateParticipants = { event ->
                val participantIds = (event.approvedParticipants + event.creatorId).joinToString(",")
                navController.navigate(Screen.RateParticipants(event.id, event.eventType.name, participantIds).route)
            },
            onBoostClick = {
                (context as? android.app.Activity)?.let { activity ->
                    val success = billingViewModel.buyEventBoost(activity, eventId)
                    if (!success) {
                        android.widget.Toast.makeText(context, "⚠️ Error: Producto 'boost' no disponible", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            onBoostWithCredit = { vm.boostWithCredit(sessionViewModel.currentUserId()) },
            credits = credits,
            isSaved = isSaved,
            onToggleSave = { vm.toggleSaveEvent(sessionViewModel.currentUserId()) },
            hasAttended = hasAttended,
            checkInState = checkInState,
            onCheckInClick = { vm.performCheckIn(sessionViewModel.currentUserId()) },
            onResetCheckInState = vm::resetCheckInState,
            actionState = actionState,
            onResetActionState = vm::resetActionState,
            joinSubmissionState = submissionState,
            onResetJoinSubmissionState = vm::resetJoinSubmissionState,
            adUnlockState = adUnlockState,
            onWatchAd = {
                (context as? android.app.Activity)?.let { activity ->
                    vm.watchAd(activity, sessionViewModel.currentUserId())
                }
            },
            onResetAdUnlockState = vm::resetAdUnlockState
        )
    }

    // ---------- QUESTIONNAIRE ----------
    composable(
        route = Screen.Questionnaire.routePattern,
        arguments = listOf(navArgument("eventId") { type = NavType.StringType })
    ) { backStackEntry ->
        val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
        val vm: EventDetailViewModel = hiltViewModel<EventDetailViewModel, EventDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(eventId) }
        )
        val uiState by vm.uiState.collectAsState()
        val submissionState by vm.joinSubmissionState.collectAsState()
        val adUnlockState by vm.adUnlockState.collectAsState()
        val activity = LocalActivity.current

        LaunchedEffect(submissionState) {
            if (submissionState is JoinSubmissionState.Success) {
                vm.resetJoinSubmissionState()
                navController.popBackStack()
            }
        }

        if (uiState is EventDetailUiState.Success) {
            QuestionnaireScreen(
                event = (uiState as EventDetailUiState.Success).event,
                submissionState = submissionState,
                onSubmit = { answers ->
                    vm.requestJoinEventWithAnswers(
                        userId = sessionViewModel.currentUserId(),
                        answers = answers
                    )
                },
                onCancel = { 
                    vm.resetJoinSubmissionState()
                    navController.popBackStack() 
                }
            )

            // 📺 AD LOGIC DIALOG FOR JOIN LIMIT
            var showAdDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            
            LaunchedEffect(submissionState) {
                if (submissionState is JoinSubmissionState.Error && 
                    (submissionState as JoinSubmissionState.Error).message == "LIMIT_REACHED") {
                    showAdDialog = true
                }
            }

            if (showAdDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { 
                        if (adUnlockState !is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.LoadingAd) {
                            showAdDialog = false
                            vm.resetJoinSubmissionState()
                        }
                    },
                    title = {
                        when (adUnlockState) {
                            is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Unlocked -> androidx.compose.material3.Text("¡Desbloqueado! 🎉")
                            else -> androidx.compose.material3.Text("Límite de Solicitudes")
                        }
                    },
                    text = {
                        androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            when (val state = adUnlockState) {
                                is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Idle -> {
                                    androidx.compose.material3.Text("Has alcanzado tu límite gratuito. Puedes ver 2 anuncios para desbloquear 1 solicitud extra.")
                                }
                                is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.LoadingAd -> {
                                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        androidx.compose.material3.CircularProgressIndicator(modifier = androidx.compose.ui.Modifier.size(24.dp), strokeWidth = 2.dp)
                                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(8.dp))
                                        androidx.compose.material3.Text("Cargando anuncio...")
                                    }
                                }
                                is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Progress -> {
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { state.watched.toFloat() / state.required.toFloat() },
                                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(8.dp)
                                    )
                                    androidx.compose.material3.Text("Has visto ${state.watched} de ${state.required} anuncios. ¡Falta poco!")
                                }
                                is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Unlocked -> {
                                    androidx.compose.material3.Text("¡Ya tienes un cupo extra! Presiona Enviar otra vez.")
                                }
                                is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Error -> {
                                    androidx.compose.material3.Text("Hubo un problema: ${state.message}", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        when (val state = adUnlockState) {
                            is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Unlocked -> {
                                androidx.compose.material3.Button(onClick = { 
                                    showAdDialog = false 
                                    vm.resetJoinSubmissionState()
                                }) {
                                    androidx.compose.material3.Text("Continuar")
                                }
                            }
                            is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.LoadingAd -> { } 
                            else -> {
                                androidx.compose.material3.Button(onClick = {
                                    if (activity != null) {
                                        vm.watchAd(activity, sessionViewModel.currentUserId())
                                    }
                                }) {
                                    androidx.compose.material3.Text(if (state is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Progress) "Ver Siguiente" else "Ver Anuncio")
                                }
                            }
                        }
                    },
                    dismissButton = {
                        if (adUnlockState !is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.LoadingAd && adUnlockState !is com.eventos.banana.ui.event.EventDetailViewModel.UnlockState.Unlocked) {
                            androidx.compose.material3.TextButton(onClick = { 
                                showAdDialog = false 
                                vm.resetJoinSubmissionState()
                            }) {
                                androidx.compose.material3.Text("Cancelar")
                            }
                        }
                    }
                )
            }
        }
    }

    // ---------- RATE PARTICIPANTS ----------
    composable(
        route = Screen.RateParticipants.routePattern,
        arguments = listOf(
            navArgument("eventId") { type = NavType.StringType },
            navArgument("eventType") { type = NavType.StringType },
            navArgument("participantIds") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
        val eventTypeStr = backStackEntry.arguments?.getString("eventType") ?: "OTRO"
        val participantIdsStr = backStackEntry.arguments?.getString("participantIds") ?: ""
        val eventType = try {
            com.eventos.banana.domain.model.EventType.valueOf(eventTypeStr)
        } catch (e: Exception) {
            com.eventos.banana.domain.model.EventType.OTRO
        }
        val participantIds = if (participantIdsStr.isNotBlank()) participantIdsStr.split(",") else emptyList()

        RateParticipantsScreen(
            eventId = eventId,
            eventType = eventType,
            currentUserId = sessionViewModel.currentUserId(),
            participantIds = participantIds,
            onBackClick = { navController.popBackStack() },
            onUserClick = { userId -> navController.navigate("public_profile/$userId") }
        )
    }

    // ---------- RATE USER ----------
    composable(
        route = Screen.RateUser.routePattern,
        arguments = listOf(
            navArgument("eventId") { type = NavType.StringType },
            navArgument("targetUserId") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
        val targetUserId = backStackEntry.arguments?.getString("targetUserId") ?: return@composable
        val vm: RateUserViewModel = hiltViewModel<RateUserViewModel, RateUserViewModel.Factory>(
            creationCallback = { factory -> factory.create(targetUserId, eventId, sessionViewModel.currentUserId()) }
        )
        val uiState by vm.uiState.collectAsState()
        RateUserScreen(
            uiState = uiState,
            onSubmit = vm::submitRating,
            onBack = { navController.popBackStack() }
        )
    }
}
