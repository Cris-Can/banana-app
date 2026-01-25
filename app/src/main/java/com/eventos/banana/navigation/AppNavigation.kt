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
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.eventos.banana.domain.model.EventDetailUiState
import com.eventos.banana.domain.model.ExactLocation
import com.eventos.banana.domain.model.SessionState
import com.eventos.banana.ui.event.*
import com.eventos.banana.ui.maps.MapLocationPickerScreen
import com.eventos.banana.ui.home.HomeScreen
import com.eventos.banana.ui.login.LoginScreen
import com.eventos.banana.ui.notifications.NotificationsScreen
import com.eventos.banana.ui.profile.ProfileScreen
import com.eventos.banana.ui.splash.SplashScreen
import com.eventos.banana.ui.messages.ConversationsScreen
import com.eventos.banana.ui.messages.ChatScreen
import com.eventos.banana.data.repository.MessageRepository
import com.eventos.banana.domain.model.Conversation
import com.eventos.banana.domain.model.Message
import com.eventos.banana.viewmodel.*

@Composable
fun AppNavigation(startDestination: String = "splash") {

    val navController = rememberNavController()
    
    // Check if we have a session to decide real start if not splash
    // ... logic simplifies to: checking sessionViewModel state later

    // ... (rest of vals)

    // ---------- SHARED PREFERENCES ----------
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("banana_prefs", android.content.Context.MODE_PRIVATE)
    }
    
    // ---------- SESSION VIEW MODEL WITH CACHE ----------
    val sessionViewModel: SessionViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SessionViewModel(sharedPreferences = sharedPreferences) as T
            }
        }
    )
    val sessionState by sessionViewModel.sessionState.collectAsState()

    // 🔒 Cache onboarding state to avoid flash on each session state change
    val hasSeenOnboarding = remember {
        androidx.compose.runtime.mutableStateOf(
            sharedPreferences.getBoolean("onboarding_seen_v2", false)
        )
    }

    // ---------- GUIDE VIEW MODEL ----------
    val guideViewModel: GuideViewModel = viewModel(
        factory = GuideViewModelFactory(sharedPreferences)
    )

    // Listen for Guide Navigation Events
    LaunchedEffect(Unit) {
        guideViewModel.navigationEvent.collect { route ->
            navController.navigate(route)
        }
    }
    
    // Logic: Start at Splash.
    // If Authenticated -> Check Onboarding -> Home/Onboarding
    // If Not Authenticated -> Login
    
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
        navController = navController,
        startDestination = "splash"
    ) {

        // ---------- SPLASH ----------
        composable("splash") {
            SplashScreen()
        }

        // ---------- ONBOARDING ----------
        composable("onboarding") {
            com.eventos.banana.ui.onboarding.OnboardingScreen(
                onFinish = { dontShowAgain ->
                    if (dontShowAgain) {
                        sharedPreferences.edit().putBoolean("onboarding_seen_v2", true).apply()
                        hasSeenOnboarding.value = true // Update cached state
                    }
                    // Navigate to Home after onboarding
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        // ---------- LOGIN ----------
        composable("login") {
            val loginUiState by sessionViewModel.loginUiState.collectAsState()
            val registerUiState by sessionViewModel.registerUiState.collectAsState()

            LoginScreen(
                loginUiState = loginUiState,
                registerUiState = registerUiState,
                onLogin = sessionViewModel::login,
                onRegister = sessionViewModel::register
            )
        }

        // ---------- HOME ----------
        composable("home") {
            val notificationViewModel: NotificationViewModel = viewModel()

            LaunchedEffect(Unit) {
                notificationViewModel.start(sessionViewModel.currentUserId())
                // 🚀 Start Guide if needed (Only on Home)
                guideViewModel.startGuide()
            }

            val notifications by notificationViewModel.notifications.collectAsState()
            val unreadCount = notifications.count { !it.read }

            // 📩 Unread Messages Count
            val msgRepo = remember { MessageRepository() }
            val conversations by msgRepo.observeConversations(sessionViewModel.currentUserId()).collectAsState(initial = emptyList())
            val unreadMessagesCount = conversations.sumOf { it.unreadCount[sessionViewModel.currentUserId()] ?: 0 }

            HomeScreen(
                sessionViewModel = sessionViewModel,
                unreadNotifications = unreadCount,
                unreadMessages = unreadMessagesCount, // Pass the count
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
                },
                onSearchClick = {
                    navController.navigate("search")
                },
                onFriendsClick = {
                    navController.navigate("friends")
                },
                onMessagesClick = {
                    navController.navigate("conversations")
                }
            )
        }

        // ---------- REST OF ROUTES ----------
        composable("create_event") { backStackEntry ->
            val vm: CreateEventViewModel = viewModel()
            val uiState by vm.uiState.collectAsState()

            // Get result from map picker
            val exactLocation = backStackEntry.savedStateHandle.get<ExactLocation>("location_result")
            LaunchedEffect(exactLocation) {
                if (exactLocation != null) {
                    vm.updateExactLocation(exactLocation)
                    // Clear the savedState to avoid re-triggering if we come back
                    backStackEntry.savedStateHandle.remove<ExactLocation>("location_result")
                }
            }
            
            val formState by vm.formState.collectAsState()

            CreateEventScreen(
                creatorId = sessionViewModel.currentUserId(),
                viewModel = vm,
                onSelectExactLocation = {
                    val route = if (formState.currentLatitude != null && formState.currentLongitude != null) {
                         "pick_location?lat=${formState.currentLatitude}&lng=${formState.currentLongitude}"
                    } else if (formState.exactLocation?.latitude != null) {
                         "pick_location?lat=${formState.exactLocation?.latitude}&lng=${formState.exactLocation?.longitude}"
                    } else {
                        "pick_location"
                    }
                    navController.navigate(route)
                },
                onSuccess = {
                    vm.resetState()
                    navController.popBackStack()
                }
            )
        }
        
        // ... (Keep other composables as they are, just ensuring context match) ...
        // I will use replace_file_content carefully to match the context block.

        // ---------- MAP LOCATION PICKER ----------
        composable(
            route = "pick_location?lat={lat}&lng={lng}",
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
                onLocationSelected = { lat, lng, addr ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        "location_result",
                        ExactLocation(lat, lng, addr)
                    )
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ---------- EVENT DETAIL ----------
        composable(
            route = "event_detail/{eventId}",
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
            val isSaved by vm.isSaved.collectAsState()
            val hasAttended by vm.hasAttended.collectAsState()

            LaunchedEffect(vm, sessionViewModel.currentUserId()) {
                 vm.loadUserInteractionState(sessionViewModel.currentUserId())
            }

            EventDetailRoute(
                uiState = uiState,
                currentUserId = sessionViewModel.currentUserId(),
                isEmailVerified = sessionViewModel.isEmailVerified,
                onJoinClick = {
                    navController.navigate("questionnaire/$eventId")
                },
                onApproveClick = vm::approveParticipant,
                onRejectClick = vm::rejectParticipant,
                onCancelEvent = { reason ->
                    vm.cancelEvent(reason)
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onCloseEvent = {
                    vm.closeEvent()
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onDeleteEvent = {
                    vm.deleteEvent()
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onRemoveParticipant = vm::removeParticipant,
                onRateUser = { targetUserId ->
                    navController.navigate("rate_user/$eventId/$targetUserId")
                },
                onUserClick = { targetUserId ->
                    if (targetUserId == sessionViewModel.currentUserId()) {
                        navController.navigate("profile")
                    } else {
                        navController.navigate("public_profile/$targetUserId")
                    }
                },
                onRateParticipants = { event ->
                    val participantIds = (event.approvedParticipants + event.creatorId).joinToString(",")
                    navController.navigate("rate_participants/${event.id}/${event.eventType.name}/$participantIds")
                },
                onConfirmEncounters = { event ->
                    val participantIds = (event.approvedParticipants + event.creatorId).joinToString(",")
                    navController.navigate("nfc_encounters/${event.id}/$participantIds")
                },
                isSaved = isSaved,
                onToggleSave = { vm.toggleSaveEvent(sessionViewModel.currentUserId()) },
                hasAttended = hasAttended
            )
        }

        // ---------- QUESTIONNAIRE ----------
        composable(
            route = "questionnaire/{eventId}",
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
            val submissionState by vm.joinSubmissionState.collectAsState()

            // 🚀 Navigation effect: return only on SUCCESS
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
            }
        }

        // ---------- RATE PARTICIPANTS (Round 11) ----------
        composable(
            route = "rate_participants/{eventId}/{eventType}/{participantIds}",
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
            
            val participantIds = if (participantIdsStr.isNotBlank()) {
                participantIdsStr.split(",")
            } else {
                emptyList()
            }

            com.eventos.banana.ui.rating.RateParticipantsScreen(
                eventId = eventId,
                eventType = eventType,
                currentUserId = sessionViewModel.currentUserId(),
                participantIds = participantIds,
                onBackClick = { navController.popBackStack() }
            )
        }

        // ---------- NFC ENCOUNTERS (Round 12) ----------
        composable(
            route = "nfc_encounters/{eventId}/{participantIds}",
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType },
                navArgument("participantIds") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            val participantIdsStr = backStackEntry.arguments?.getString("participantIds") ?: ""
            
            val participantIds = if (participantIdsStr.isNotBlank()) {
                participantIdsStr.split(",")
            } else {
                emptyList()
            }

            com.eventos.banana.ui.nfc.NFCTapScreen(
                eventId = eventId,
                currentUserId = sessionViewModel.currentUserId(),
                participantIds = participantIds,
                onBackClick = { navController.popBackStack() }
            )
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
                onNotificationClick = { notification ->
                    notificationViewModel.markAllAsRead(userId)
                    
                    if (notification.type == com.eventos.banana.domain.model.NotificationType.FRIEND_REQUEST) {
                        navController.navigate("friends")
                    } else if (notification.type == com.eventos.banana.domain.model.NotificationType.NEW_MESSAGE) {
                        notification.eventId?.let { conversationId ->
                            navController.navigate("chat/$conversationId")
                        }
                    } else {
                        notification.eventId?.let { eventId ->
                             navController.navigate("event_detail/$eventId")
                        }
                    }
                }
            )
        }

        // ---------- RATE USER ----------
        composable(
            route = "rate_user/{eventId}/{targetUserId}",
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType },
                navArgument("targetUserId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            val targetUserId = backStackEntry.arguments?.getString("targetUserId") ?: return@composable

            val vm: com.eventos.banana.viewmodel.RateUserViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return com.eventos.banana.viewmodel.RateUserViewModel(
                            targetUserId = targetUserId,
                            eventId = eventId,
                            currentUserId = sessionViewModel.currentUserId()
                        ) as T
                    }
                }
            )

            val uiState by vm.uiState.collectAsState()

            com.eventos.banana.ui.rating.RateUserScreen(
                uiState = uiState,
                onSubmit = vm::submitRating,
                onBack = { navController.popBackStack() }
            )
        }

        // ---------- PROFILE ----------
        composable("profile") {
            ProfileScreen(
                sessionViewModel = sessionViewModel,
                onBack = { navController.popBackStack() },
                onFriendsClick = { navController.navigate("friends") },
                onEventClick = { eventId ->
                    navController.navigate("event_detail/$eventId")
                }
            )
        }

        // ---------- SEARCH ----------
        composable("search") {
            com.eventos.banana.ui.search.UnifiedSearchScreen(
                navController = navController,
                currentUserId = sessionViewModel.currentUserId() ?: ""
            )
        }

        // ---------- FRIENDS LIST ----------
        composable("friends") {
            com.eventos.banana.ui.profile.FriendListScreen(
                currentUserId = sessionViewModel.currentUserId(),
                onBack = { navController.popBackStack() },
                onUserClick = { userId ->
                     navController.navigate("public_profile/$userId")
                }
            )
        }

        // ---------- PUBLIC PROFILE ----------
        composable(
            route = "public_profile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            
            com.eventos.banana.ui.profile.PublicProfileScreen(
                targetUserId = userId,
                onBack = { navController.popBackStack() },
                isCurrentUserVerified = sessionViewModel.isEmailVerified,
                onMessageClick = { targetUserId ->
                    navController.navigate("start_chat/$targetUserId")
                }
            )
        }

        // ---------- CONVERSATIONS ----------
        composable("conversations") {
            val messageRepository = remember { MessageRepository() }
            val currentUserId = sessionViewModel.currentUserId()
            val conversations by messageRepository.observeConversations(currentUserId)
                .collectAsState(initial = emptyList())

            ConversationsScreen(
                conversations = conversations,
                currentUserId = currentUserId,
                onConversationClick = { conversationId ->
                    navController.navigate("chat/$conversationId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ---------- CHAT ----------
        composable(
            route = "chat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            val messageRepository = remember { MessageRepository() }
            val currentUserId = sessionViewModel.currentUserId()
            
            // Mark as read when entering chat
            LaunchedEffect(conversationId) {
                messageRepository.markConversationAsRead(conversationId, currentUserId)
            }
            
            val messages by messageRepository.observeMessages(conversationId)
                .collectAsState(initial = emptyList<com.eventos.banana.domain.model.Message>())
            
            // Get other user nickname from first message or conversation
            val otherNickname = "Chat"
            val scope = rememberCoroutineScope()
            
            ChatScreen(
                otherUserNickname = otherNickname,
                messages = messages,
                currentUserId = currentUserId,
                onSendMessage = { content ->
                    scope.launch {
                        messageRepository.sendMessage(conversationId, currentUserId, content)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ---------- START CHAT (from profile) ----------
        composable(
            route = "start_chat/{targetUserId}",
            arguments = listOf(navArgument("targetUserId") { type = NavType.StringType })
        ) { backStackEntry ->
            val targetUserId = backStackEntry.arguments?.getString("targetUserId") ?: return@composable
            val messageRepository = remember { MessageRepository() }
            val currentUserId = sessionViewModel.currentUserId()
            val profileUiState by sessionViewModel.profileUiState.collectAsState()
            val currentNickname = profileUiState.profile?.nickname ?: "Usuario"

            // Placeholder: In real app, fetch target user's nickname
            LaunchedEffect(Unit) {
                val result = messageRepository.getOrCreateConversation(
                    currentUserId = currentUserId,
                    otherUserId = targetUserId,
                    currentUserNickname = currentNickname,
                    otherUserNickname = "Usuario"
                )
                result.onSuccess { conversationId ->
                    navController.navigate("chat/$conversationId") {
                        popUpTo("start_chat/$targetUserId") { inclusive = true }
                    }
                }
            }

            // Loading indicator while creating/finding conversation
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        // ---------- EMAIL VERIFICATION ----------
        composable("verification") {
            com.eventos.banana.ui.login.EmailVerificationScreen(
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
        
        // 🌟 GUIDE OVERLAY (Always on top)
        com.eventos.banana.ui.components.GuideOverlay(
            viewModel = guideViewModel
        )
    }

    // ---------- SESSION REDIRECTION ----------
    // React to sessionState changes and verification check completion
    LaunchedEffect(sessionState, sessionViewModel.isVerificationChecked) {
        when (sessionState) {
            SessionState.LOADING -> Unit

            SessionState.NOT_AUTHENTICATED -> {
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }

            SessionState.AUTHENTICATED -> {
                // 🔒 Wait for verification check to complete before navigating
                if (!sessionViewModel.isVerificationChecked) {
                    // Still checking, don't navigate yet
                    return@LaunchedEffect
                }
                
                // 🛑 Strict Verification Check
                if (!sessionViewModel.isEmailVerified) {
                    navController.navigate("verification") {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    // Verified -> Check Onboarding -> Home
                    if (!hasSeenOnboarding.value) {
                        navController.navigate("onboarding") {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        val route = if (startDestination != "splash") startDestination else "home"
                        navController.navigate(route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
        }
    }
}
