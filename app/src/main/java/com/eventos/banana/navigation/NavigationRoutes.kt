package com.eventos.banana.navigation

sealed class Screen(val route: String) {
    // Auth
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Onboarding : Screen("onboarding")
    object Verification : Screen("verification")

    // Home
    object Home : Screen("home")
    object WorldMap : Screen("world_map")
    object Notifications : Screen("notifications")
    object Search : Screen("search")

    // Events
    object CreateEvent : Screen("create_event")
    data class PickLocation(val lat: Double? = null, val lng: Double? = null) : 
        Screen("pick_location" + if (lat != null && lng != null) "?lat=$lat&lng=$lng" else "") {
        companion object { const val routePattern = "pick_location?lat={lat}&lng={lng}" }
    }
    data class EventDetail(val eventId: String, val tab: Int = 0) : 
        Screen("event_detail/$eventId?tab=$tab") {
        companion object { const val routePattern = "event_detail/{eventId}?tab={tab}" }
    }
    data class Questionnaire(val eventId: String) : Screen("questionnaire/$eventId") {
        companion object { const val routePattern = "questionnaire/{eventId}" }
    }
    data class RateParticipants(val eventId: String, val eventType: String, val participantIds: String) : 
        Screen("rate_participants/$eventId/$eventType/$participantIds") {
        companion object { const val routePattern = "rate_participants/{eventId}/{eventType}/{participantIds}" }
    }
    data class RateUser(val eventId: String, val targetUserId: String) : Screen("rate_user/$eventId/$targetUserId") {
        companion object { const val routePattern = "rate_user/{eventId}/{targetUserId}" }
    }

    // Profile
    object Leaderboard : Screen("leaderboard")
    object Profile : Screen("profile")
    data class UserRatings(val userId: String) : Screen("user_ratings/$userId") {
        companion object { const val routePattern = "user_ratings/{userId}" }
    }
    data class ProfileViews(val userId: String) : Screen("profile_views/$userId") {
        companion object { const val routePattern = "profile_views/{userId}" }
    }
    object Settings : Screen("settings")
    object BlockedUsers : Screen("blocked_users")
    object AdminDashboard : Screen("admin_dashboard")
    data class Friends(val tab: Int = 0) : Screen("friends?tab=$tab") {
        companion object { const val routePattern = "friends?tab={tab}" }
    }
    data class PublicProfile(val userId: String) : Screen("public_profile/$userId") {
        companion object { const val routePattern = "public_profile/{userId}" }
    }

    // Monetization
    object Gold : Screen("gold")
    object AppIcons : Screen("app_icons")

    // Chat
    object Conversations : Screen("conversations")
    data class Chat(val conversationId: String) : Screen("chat/$conversationId") {
        companion object { const val routePattern = "chat/{conversationId}" }
    }
    data class StartChat(val targetUserId: String) : Screen("start_chat/$targetUserId") {
        companion object { const val routePattern = "start_chat/{targetUserId}" }
    }
}
