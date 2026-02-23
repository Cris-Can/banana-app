package com.eventos.banana.domain.usecase.rating

import com.eventos.banana.data.repository.RatingRepository
import com.eventos.banana.domain.model.EventType
import javax.inject.Inject

class SubmitRatingUseCase @Inject constructor(
    private val ratingRepository: RatingRepository
) {
    suspend operator fun invoke(
        eventId: String,
        eventType: EventType,
        fromUserId: String,
        toUserId: String,
        score: Double,
        comment: String?
    ): Result<String> {
        return ratingRepository.submitRating(
            eventId = eventId,
            eventType = eventType,
            fromUserId = fromUserId,
            toUserId = toUserId,
            score = score,
            comment = comment
        )
    }
}
