package com.eventos.banana.domain.usecase.rating

import com.eventos.banana.data.repository.RatingRepository
import com.eventos.banana.domain.model.EventType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubmitRatingUseCaseTest {

    private lateinit var repository: RatingRepository
    private lateinit var useCase: SubmitRatingUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = SubmitRatingUseCase(repository)
    }

    @Test
    fun `invoke calls repository and returns success`() = runTest {
        // Given
        val eventId = "event1"
        val eventType = EventType.OTRO
        val fromUserId = "user1"
        val toUserId = "user2"
        val score = 5.0
        val comment = "Great!"
        val ratingId = "rating1"

        coEvery { 
            repository.submitRating(eventId, eventType, fromUserId, toUserId, score, comment) 
        } returns Result.success(ratingId)

        // When
        val result = useCase(eventId, eventType, fromUserId, toUserId, score, comment)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(ratingId, result.getOrNull())
        
        coVerify(exactly = 1) { 
            repository.submitRating(eventId, eventType, fromUserId, toUserId, score, comment) 
        }
    }
    
    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        // Given
        val exception = Exception("No puedes puntuarte a ti mismo")
        coEvery { 
            repository.submitRating(any(), any(), any(), any(), any(), any()) 
        } returns Result.failure(exception)

        // When
        val result = useCase("e1", EventType.OTRO, "u1", "u1", 5.0, null)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception.message, result.exceptionOrNull()?.message)
    }
}
