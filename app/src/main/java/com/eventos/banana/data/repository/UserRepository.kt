package com.eventos.banana.data.repository

import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UserRepository (Facade Pattern)
 * 
 * Esta clase actúa como el punto de entrada único para todas las operaciones relacionadas con usuarios,
 * delegando la lógica de negocio a repositorios especializados para cumplir con el Principio de Responsabilidad Única.
 */
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val notificationRepository: NotificationRepository,
    private val storageDataSource: com.eventos.banana.data.remote.storage.FirebaseStorageDataSource,
    private val userSocialRepository: UserSocialRepository,
    private val userGamificationRepository: UserGamificationRepository,
    private val userMediaRepository: UserMediaRepository,
    private val userMessagingRepository: UserMessagingRepository,
    private val userAdminRepository: UserAdminRepository,
    private val userCoreRepository: UserCoreRepository
) {
    private val users = firestore.collection("users")

    // =====================================================
    // 🏠 CORE PROFILE MANAGEMENT (Delegated to UserCoreRepository)
    // =====================================================
    suspend fun saveUserProfile(profile: UserProfile) = userCoreRepository.saveUserProfile(profile)
    suspend fun getUserProfile(uid: String, forceRefresh: Boolean = false) = userCoreRepository.getUserProfile(uid, forceRefresh)
    suspend fun getUsers(uids: List<String>) = userCoreRepository.getUsers(uids)
    suspend fun updateNickname(uid: String, nickname: String) = userCoreRepository.updateNickname(uid, nickname)
    fun observeUserProfile(uid: String): Flow<UserProfile> = userCoreRepository.observeUserProfile(uid)
    suspend fun updateLocation(uid: String, region: String, commune: String, country: String? = null, lat: Double? = null, lng: Double? = null, geohash: String? = null) = 
        userCoreRepository.updateLocation(uid, region, commune, country, lat, lng, geohash)
    suspend fun updateSearchRadius(uid: String, radiusKm: Int) = userCoreRepository.updateSearchRadius(uid, radiusKm)
    suspend fun updateVerificationStatus(uid: String, verified: Boolean) = userCoreRepository.updateVerificationStatus(uid, verified)
    suspend fun updateSocialProfile(uid: String, aboutMe: String, interests: List<String>) = userCoreRepository.updateSocialProfile(uid, aboutMe, interests)
    suspend fun updateAppTheme(uid: String, theme: String) = userCoreRepository.updateAppTheme(uid, theme)

    // =====================================================
    // 🔍 SEARCH & DISCOVERY (Delegated to UserCoreRepository)
    // =====================================================
    suspend fun searchUsers(query: String) = userCoreRepository.searchUsers(query)
    suspend fun getUsersByRegion(region: String, excludeUid: String) = userCoreRepository.getUsersByRegion(region, excludeUid)
    suspend fun getUsersByCommune(commune: String, excludeUid: String) = userCoreRepository.getUsersByCommune(commune, excludeUid)
    suspend fun getUsersByProximity(geohash: String, excludeUid: String, limit: Int = 30, precision: Int = 4) = userCoreRepository.getUsersByProximity(geohash, excludeUid, limit, precision)
    
    fun observeActualFriendships(uid: String) = userCoreRepository.observeActualFriendships(uid)
    fun observeActualFriendRequestsReceived(uid: String) = userCoreRepository.observeActualFriendRequestsReceived(uid)

    // =====================================================
    // 🔔 NOTIFICATIONS & FCM (Delegated to UserMessagingRepository)
    // =====================================================
    suspend fun saveFcmToken(userId: String, token: String) = userMessagingRepository.saveFcmToken(userId, token)
    suspend fun verifyAndSyncFcmToken(userId: String) = userMessagingRepository.verifyAndSyncFcmToken(userId)
    suspend fun saveNotificationPreferences(userId: String, region: String, commune: String, eventsInMyCommune: Boolean) = 
        userMessagingRepository.saveNotificationPreferences(userId, region, commune, eventsInMyCommune)
    suspend fun updateNotifyEventsByCommune(uid: String, enabled: Boolean, region: String?, commune: String?) {
        userMessagingRepository.updateNotifyEventsByCommune(uid, enabled, region, commune)
        val profile = getUserProfile(uid, forceRefresh = true)
        if (profile != null) syncNotificationTopics(profile)
    }
    suspend fun updateNotifyEventWall(uid: String, enabled: Boolean) = userMessagingRepository.updateNotifyEventWall(uid, enabled)
    suspend fun syncNotificationTopics(profile: UserProfile) = userMessagingRepository.syncNotificationTopics(profile)
    suspend fun ensureNotificationsActive(uid: String) {
        val prof = getUserProfile(uid, false) ?: return
        userMessagingRepository.verifyAndSyncFcmToken(uid)
        userMessagingRepository.syncNotificationTopics(prof)
    }
    suspend fun updateSubscribedCategories(uid: String, categories: List<String>) {
        val oldProfile = getUserProfile(uid)
        val oldCategories = oldProfile?.subscribedCategories ?: emptyList()
        userMessagingRepository.updateSubscribedCategories(uid, categories, oldCategories)
    }

    // =====================================================
    // 🤝 SOCIAL & FRIENDS (Delegated to UserSocialRepository)
    // =====================================================
    suspend fun sendFriendRequest(targetUid: String) = userSocialRepository.sendFriendRequest(targetUid)
    suspend fun acceptFriendRequest(requesterUid: String) = userSocialRepository.acceptFriendRequest(requesterUid)
    suspend fun rejectFriendRequest(requesterUid: String) = userSocialRepository.rejectFriendRequest(requesterUid)
    suspend fun removeFriend(friendUid: String) = userSocialRepository.removeFriend(friendUid)
    suspend fun toggleEventSaved(uid: String, eventId: String, isSaved: Boolean) = userSocialRepository.toggleEventSaved(uid, eventId, isSaved)
    suspend fun recordProfileView(visitorUid: String, targetUid: String) = userSocialRepository.recordProfileView(visitorUid, targetUid)
    suspend fun getProfileViews(uid: String) = userSocialRepository.getProfileViews(uid)
    suspend fun blockUser(currentUid: String, targetUid: String) = userSocialRepository.blockUser(currentUid, targetUid)
    suspend fun unblockUser(currentUid: String, targetUid: String) = userSocialRepository.unblockUser(currentUid, targetUid)
    suspend fun getBlockedUsers(uid: String): List<String> = getUserProfile(uid)?.blockedUsers ?: emptyList()
    suspend fun getBlockedUsersProfiles(uid: String): List<UserProfile> {
        val blockedIds = getBlockedUsers(uid)
        if (blockedIds.isEmpty()) return emptyList()
        return getUsers(blockedIds)
    }

    // =====================================================
    // 🏆 GAMIFICATION (Delegated to UserGamificationRepository)
    // =====================================================
    suspend fun incrementEventsRequested(uid: String) = userGamificationRepository.incrementEventsRequested(uid)
    suspend fun incrementEventsAttended(uid: String) = userGamificationRepository.incrementEventsAttended(uid)
    suspend fun incrementEventsCreatedLifetime(uid: String) = userGamificationRepository.incrementEventsCreatedLifetime(uid)
    @Deprecated("Aggregation is handled server-side by Cloud Function onRatingCreated. Use only for admin/debug manual recalculation.")
    suspend fun recalculateUserStats(uid: String): Result<String> {
        val result = userGamificationRepository.recalculateUserStats(uid)
        getUserProfile(uid, forceRefresh = true)
        return result
    }
    suspend fun getTopUsers(limit: Int = 20, startAfter: com.google.firebase.firestore.DocumentSnapshot? = null): Result<Pair<List<UserProfile>, com.google.firebase.firestore.DocumentSnapshot?>> = 
        userGamificationRepository.getTopUsers(limit, startAfter)

    suspend fun getTopUsersByRating(limit: Int = 20, startAfter: com.google.firebase.firestore.DocumentSnapshot? = null): Result<Pair<List<UserProfile>, com.google.firebase.firestore.DocumentSnapshot?>> = 
        userGamificationRepository.getTopUsersByRating(limit, startAfter)

    // \ud83d\udce1 REALTIME delegates
    fun observeTopUsers(limit: Int = 20): kotlinx.coroutines.flow.Flow<List<UserProfile>> =
        userGamificationRepository.observeTopUsers(limit)

    fun observeTopUsersByRating(limit: Int = 20): kotlinx.coroutines.flow.Flow<List<UserProfile>> =
        userGamificationRepository.observeTopUsersByRating(limit)
    suspend fun setGoldStatus(uid: String, isGold: Boolean) {
        val profile = getUserProfile(uid, forceRefresh = true) ?: return
        userGamificationRepository.setGoldStatus(uid, isGold, profile.isFounder, profile.subscriptionType)
    }

    // =====================================================
    // 📸 MEDIA (Delegated to UserMediaRepository)
    // =====================================================
    suspend fun uploadProfilePhoto(uid: String, imageBytes: ByteArray?, isProfilePicture: Boolean, isCoverPhoto: Boolean = false) = 
        userMediaRepository.uploadProfilePhoto(uid, imageBytes, isProfilePicture, isCoverPhoto)
    suspend fun deletePhoto(uid: String, photoUrl: String) = userMediaRepository.deletePhoto(uid, photoUrl)

    // =====================================================
    // 🛡️ ADMIN & MODERATION (Delegated to UserAdminRepository)
    // =====================================================
    suspend fun reportUser(reporterUid: String, reportedUid: String, reason: String) = userAdminRepository.reportUser(reporterUid, reportedUid, reason)
    suspend fun getPendingReports() = userAdminRepository.getPendingReports()
    suspend fun resolveReport(reportId: String, action: String) = userAdminRepository.resolveReport(reportId, action)
    suspend fun banUser(uid: String) = userAdminRepository.banUser(uid)
    suspend fun unbanUser(uid: String) = userAdminRepository.unbanUser(uid)
    suspend fun generateFounderCode(createdByUid: String, durationDays: Int? = null) = userAdminRepository.generateFounderCode(createdByUid, durationDays)
    suspend fun cleanupUsersDatabase(): Result<String> = userAdminRepository.cleanupUsersDatabase()
}
