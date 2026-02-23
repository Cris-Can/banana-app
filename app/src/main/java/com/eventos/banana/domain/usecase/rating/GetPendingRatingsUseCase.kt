package com.eventos.banana.domain.usecase.rating

import com.eventos.banana.data.repository.RatingRepository
import javax.inject.Inject

class GetPendingRatingsUseCase @Inject constructor(
    private val ratingRepository: RatingRepository
) {
    suspend operator fun invoke(
        eventId: String,
        currentUserId: String,
        approvedParticipants: List<String>,
        creatorId: String
    ): Result<List<String>> {
        return ratingRepository.getUsersToRate(
            eventId = eventId,
            currentUserId = currentUserId,
            approvedParticipants = approvedParticipants,
            creatorId = creatorId
        )
    }
}
