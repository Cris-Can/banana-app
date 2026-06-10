package com.eventos.banana.data.remote.model

import com.eventos.banana.domain.model.*
import com.google.firebase.firestore.PropertyName

/**
 * Data Transfer Object for Firestore synchronization of Events.
 * Keeps infrastructure-specific annotations away from domain logic.
 */
data class EventDto(
    val id: String = "",
    val creatorId: String = "",
    val imageUrl: String? = null,
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val eventType: String = "OTRO",
    val country: String = "",
    val region: String = "",
    val commune: String = "",
    val notifyEventsByCommune: Boolean = false,
    val fcmToken: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val exactLatitude: Double? = null,
    val exactLongitude: Double? = null,
    val exactAddress: String? = null,
    val eventTimestamp: Long = 0L,
    val createdAt: Long = 0L,
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    
    val expiresAt: Long? = null,
    val maxParticipants: Int = 0,
    @get:PropertyName("isPublic")
    @set:PropertyName("isPublic")
    var isPublic: Boolean = false,
    val approvalRequired: Boolean = true,
    val joinQuestions: List<JoinQuestion> = emptyList(),
    val status: String = "OPEN",
    val cancelledAt: Long? = null,
    val cancelReason: String? = null,
    val approvedParticipants: List<String> = emptyList(),
    val pendingRequests: List<JoinRequest> = emptyList(),
    val rejectedParticipants: List<String> = emptyList(),
    val minimumScore: Double? = null,
    val ratingDeadline: Long? = null,
    val canBeRated: Boolean = false,
    val isBoosted: Boolean = false,
    val boostExpiry: Long = 0L,
    val geohash: String? = null,
    val notificationRange: String = "COMMUNE",
    
    @get:PropertyName("isAdultContent") @set:PropertyName("isAdultContent")
    var isAdultContent: Boolean = false
) {
    fun toDomain(): Event {
        return Event(
            id = id,
            creatorId = creatorId,
            imageUrl = imageUrl,
            title = title,
            description = description,
            category = category,
            eventType = try { EventType.valueOf(eventType) } catch (e: Exception) { EventType.OTRO },
            country = country,
            region = region,
            commune = commune,
            notifyEventsByCommune = notifyEventsByCommune,
            fcmToken = fcmToken,
            address = address,
            latitude = latitude,
            longitude = longitude,
            exactLatitude = exactLatitude,
            exactLongitude = exactLongitude,
            exactAddress = exactAddress,
            eventTimestamp = eventTimestamp,
            createdAt = createdAt,
            startAt = startAt,
            endAt = endAt,
            expiresAt = expiresAt,
            maxParticipants = maxParticipants,
            isPublic = isPublic,
            approvalRequired = approvalRequired,
            joinQuestions = joinQuestions,
            status = try { EventStatus.valueOf(status) } catch (e: Exception) { EventStatus.OPEN },
            cancelledAt = cancelledAt,
            cancelReason = cancelReason,
            approvedParticipants = approvedParticipants,
            pendingRequests = pendingRequests,
            rejectedParticipants = rejectedParticipants,
            minimumScore = minimumScore,
            ratingDeadline = ratingDeadline,
            canBeRated = canBeRated,
            isBoosted = isBoosted,
            boostExpiry = boostExpiry,
            geohash = geohash,
            notificationRange = notificationRange,
            isAdultContent = isAdultContent
        )
    }

    companion object {
        fun fromDomain(domain: Event): EventDto {
            return EventDto(
                id = domain.id,
                creatorId = domain.creatorId,
                imageUrl = domain.imageUrl,
                title = domain.title,
                description = domain.description,
                category = domain.category,
                eventType = domain.eventType.name,
                country = domain.country,
                region = domain.region,
                commune = domain.commune,
                notifyEventsByCommune = domain.notifyEventsByCommune,
                fcmToken = domain.fcmToken,
                address = domain.address,
                latitude = domain.latitude,
                longitude = domain.longitude,
                exactLatitude = domain.exactLatitude,
                exactLongitude = domain.exactLongitude,
                exactAddress = domain.exactAddress,
                eventTimestamp = domain.eventTimestamp,
                createdAt = domain.createdAt,
                startAt = domain.startAt,
                endAt = domain.endAt,
                expiresAt = domain.expiresAt,
                maxParticipants = domain.maxParticipants,
                isPublic = domain.isPublic,
                approvalRequired = domain.approvalRequired,
                joinQuestions = domain.joinQuestions,
                status = domain.status.name,
                cancelledAt = domain.cancelledAt,
                cancelReason = domain.cancelReason,
                approvedParticipants = domain.approvedParticipants,
                pendingRequests = domain.pendingRequests,
                rejectedParticipants = domain.rejectedParticipants,
                minimumScore = domain.minimumScore,
                ratingDeadline = domain.ratingDeadline,
                canBeRated = domain.canBeRated,
                isBoosted = domain.isBoosted,
                boostExpiry = domain.boostExpiry,
                geohash = domain.geohash,
                notificationRange = domain.notificationRange,
                isAdultContent = domain.isAdultContent
            )
        }
    }
}
