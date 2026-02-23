package com.eventos.banana.domain.usecase.rating

import com.eventos.banana.data.repository.RatingRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetPendingRatingsUseCaseTest {

    private lateinit var repository: RatingRepository
    private lateinit var useCase: GetPendingRatingsUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = GetPendingRatingsUseCase(repository)
    }

    @Test
    fun `invoke returns empty list when all users are already rated`() = runTest {
        // Given
        val eventId = "event1"
        val currentUserId = "u1"
        val approvedParticipants = listOf("u2", "u3")
        val creatorId = "creator"

        coEvery { 
            repository.getUsersToRate(eventId, currentUserId, approvedParticipants, creatorId)
        } returns Result.success(emptyList())

        // When
        val result = useCase(eventId, currentUserId, approvedParticipants, creatorId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
    }
    
    @Test
    fun `invoke returns pending users list`() = runTest {
        // Given
        val expectedList = listOf("u2", "creator")
        coEvery { 
            repository.getUsersToRate(any(), any(), any(), any())
        } returns Result.success(expectedList)

        // When
        val result = useCase("e1", "u1", listOf("u2"), "creator")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedList, result.getOrNull())
    }
}
