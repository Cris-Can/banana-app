package com.eventos.banana.data.remote.model

import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.PropertyName

/**
 * Data Transfer Object for Firestore synchronization of User Profiles.
 */
data class UserProfileDto(
    val uid: String = "",
    val email: String = "",
    val nickname: String = "",
    val birthDate: Long? = null,
    val age: Int? = null,
    val region: String? = null,
    val commune: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geohash: String? = null,
    val searchRadiusKm: Int = 20,
    val notifyEventsByCommune: Boolean = false,
    val notifyEventWall: Boolean = true,
    val notifyByInterest: Boolean = true,
    val fcmToken: String? = null,
    val appTheme: String = "BANANA",
    
    @get:PropertyName("isVerified") @set:PropertyName("isVerified") 
    var isVerified: Boolean = false,
    
    val score: Int = 0,
    val ratingSum: Double = 0.0,
    val ratingCount: Int = 0,
    val averageScore: Double = 0.0,
    
    @get:PropertyName("isGold") @set:PropertyName("isGold") 
    var isGoldStored: Boolean = false,
    
    @get:PropertyName("premium") @set:PropertyName("premium") 
    var isPremiumStored: Boolean = false,
    
    @get:PropertyName("isFounder") @set:PropertyName("isFounder") 
    var isFounder: Boolean = false,
    
    val aboutMe: String = "",
    val interests: List<String> = emptyList(),
    val profilePictureUrl: String? = null,
    val coverPhotoUrl: String? = null,
    val photos: List<String> = emptyList(),
    
    val profileViews: Int = 0,
    val recentViewers: List<String> = emptyList(),
    
    val friends: List<String> = emptyList(),
    val friendCount: Int = 0,
    val friendRequestsReceived: List<String> = emptyList(),
    val pendingRequestsReceivedCount: Int = 0,
    val friendRequestsSent: List<String> = emptyList(),
    val pendingRequestsSentCount: Int = 0,
    val blockedUsers: List<String> = emptyList(),

    var admin: Boolean = false,
    
    @get:PropertyName("banned") @set:PropertyName("banned") 
    var isBanned: Boolean = false,

    val savedEventIds: List<String> = emptyList(),
    val subscriptionType: String = "FREE",
    val subscriptionExpiry: Long = 0,
    val currentCycleStartDate: Long = System.currentTimeMillis(),
    val eventsCreatedInCycle: Int = 0,
    val joinRequestsInCycle: Int = 0,
    val adEventsUnlocked: Int = 0,
    val adsWatchedProgress: Int = 0,
    val subscribedCategories: List<String> = emptyList(),
    val eventsRequestedCount: Int = 0,
    val eventsAttendedCount: Int = 0,
    val eventsCreatedLifetime: Int = 0,
    // 🔄 Guard de idempotencia para migración de usuarios legacy (primeros 40 founders)
    @get:PropertyName("isLegacyMigrated") @set:PropertyName("isLegacyMigrated")
    var isLegacyMigrated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): UserProfile {
        return UserProfile(
            uid = uid,
            email = email,
            nickname = nickname,
            birthDate = birthDate,
            age = age,
            region = region,
            commune = commune,
            country = country,
            latitude = latitude,
            longitude = longitude,
            geohash = geohash,
            searchRadiusKm = searchRadiusKm,
            notifyEventsByCommune = notifyEventsByCommune,
            notifyEventWall = notifyEventWall,
            notifyByInterest = notifyByInterest,
            fcmToken = fcmToken,
            appTheme = appTheme,
            isVerified = isVerified,
            score = score,
            ratingSum = ratingSum,
            ratingCount = ratingCount,
            averageScore = averageScore,
            isGoldStored = isGoldStored,
            isPremiumStored = isPremiumStored,
            isFounder = isFounder,
            aboutMe = aboutMe,
            interests = interests,
            profilePictureUrl = profilePictureUrl,
            coverPhotoUrl = coverPhotoUrl,
            photos = photos,
            profileViews = profileViews,
            recentViewers = recentViewers,
            friends = friends,
            friendCount = friendCount,
            friendRequestsReceived = friendRequestsReceived,
            pendingRequestsReceivedCount = pendingRequestsReceivedCount,
            friendRequestsSent = friendRequestsSent,
            pendingRequestsSentCount = pendingRequestsSentCount,
            blockedUsers = blockedUsers,
            admin = admin,
            isBanned = isBanned,
            savedEventIds = savedEventIds,
            subscriptionType = subscriptionType,
            subscriptionExpiry = subscriptionExpiry,
            currentCycleStartDate = currentCycleStartDate,
            eventsCreatedInCycle = eventsCreatedInCycle,
            joinRequestsInCycle = joinRequestsInCycle,
            adEventsUnlocked = adEventsUnlocked,
            adsWatchedProgress = adsWatchedProgress,
            subscribedCategories = subscribedCategories,
            eventsRequestedCount = eventsRequestedCount,
            eventsAttendedCount = eventsAttendedCount,
            eventsCreatedLifetime = eventsCreatedLifetime,
            isLegacyMigrated = isLegacyMigrated,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(domain: UserProfile): UserProfileDto {
            return UserProfileDto(
                uid = domain.uid,
                email = domain.email,
                nickname = domain.nickname,
                birthDate = domain.birthDate,
                age = domain.age,
                region = domain.region,
                commune = domain.commune,
                country = domain.country,
                latitude = domain.latitude,
                longitude = domain.longitude,
                geohash = domain.geohash,
                searchRadiusKm = domain.searchRadiusKm,
                notifyEventsByCommune = domain.notifyEventsByCommune,
                notifyEventWall = domain.notifyEventWall,
                notifyByInterest = domain.notifyByInterest,
                fcmToken = domain.fcmToken,
                appTheme = domain.appTheme,
                isVerified = domain.isVerified,
                score = domain.score,
                ratingSum = domain.ratingSum,
                ratingCount = domain.ratingCount,
                averageScore = domain.averageScore,
                isGoldStored = domain.isGoldStored,
                isPremiumStored = domain.isPremiumStored,
                isFounder = domain.isFounder,
                aboutMe = domain.aboutMe,
                interests = domain.interests,
                profilePictureUrl = domain.profilePictureUrl,
                coverPhotoUrl = domain.coverPhotoUrl,
                photos = domain.photos,
                profileViews = domain.profileViews,
                recentViewers = domain.recentViewers,
                friends = domain.friends,
                friendCount = domain.friendCount,
                friendRequestsReceived = domain.friendRequestsReceived,
                pendingRequestsReceivedCount = domain.pendingRequestsReceivedCount,
                friendRequestsSent = domain.friendRequestsSent,
                pendingRequestsSentCount = domain.pendingRequestsSentCount,
                blockedUsers = domain.blockedUsers,
                admin = domain.admin,
                isBanned = domain.isBanned,
                savedEventIds = domain.savedEventIds,
                subscriptionType = domain.subscriptionType,
                subscriptionExpiry = domain.subscriptionExpiry,
                currentCycleStartDate = domain.currentCycleStartDate,
                eventsCreatedInCycle = domain.eventsCreatedInCycle,
                joinRequestsInCycle = domain.joinRequestsInCycle,
                adEventsUnlocked = domain.adEventsUnlocked,
                adsWatchedProgress = domain.adsWatchedProgress,
                subscribedCategories = domain.subscribedCategories,
                eventsRequestedCount = domain.eventsRequestedCount,
                eventsAttendedCount = domain.eventsAttendedCount,
                eventsCreatedLifetime = domain.eventsCreatedLifetime,
                isLegacyMigrated = domain.isLegacyMigrated,
                createdAt = domain.createdAt
            )
        }
    }
}
