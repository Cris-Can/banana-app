package com.eventos.banana.ui.rating

import com.eventos.banana.MainDispatcherRule
import com.eventos.banana.core.security.RateLimitManager
import com.eventos.banana.core.security.RateLimitResult
import com.eventos.banana.data.repository.RatingRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.EventType
import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.ui.util.ResultState
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateUserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var ratingRepository: RatingRepository
    private lateinit var userRepository: UserRepository
    private lateinit var rateLimitManager: RateLimitManager

    private val targetUserId = "target_user_1"
    private val eventId = "event_1"
    private val currentUserId = "current_user_1"

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        ratingRepository = mockk()
        userRepository = mockk()
        rateLimitManager = mockk()

        // Default mock behaviors for initialization
        coEvery { ratingRepository.hasUserRated(any(), any(), any()) } returns false
        coEvery { userRepository.getUserProfile(any()) } returns UserProfile(uid = targetUserId, nickname = "TargetNickname")
        coEvery { rateLimitManager.checkRateLimit(any()) } returns RateLimitResult(success = true)
    }

    private fun createViewModel(): RateUserViewModel {
        return RateUserViewModel(
            targetUserId = targetUserId,
            eventId = eventId,
            currentUserId = currentUserId,
            ratingRepository = ratingRepository,
            userRepository = userRepository,
            rateLimitManager = rateLimitManager
        )
    }

    @Test
    fun `loadTargetProfile success updates uiState with target nickname`() = runTest {
        // Given
        coEvery { ratingRepository.hasUserRated(eventId, currentUserId, targetUserId) } returns true
        coEvery { userRepository.getUserProfile(targetUserId) } returns UserProfile(uid = targetUserId, nickname = "Amazing Banana")

        // When
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoadingData)
        assertEquals("Amazing Banana", state.targetNickname)
        assertTrue(state.alreadyRated)
        assertNull(state.loadError)
    }

    @Test
    fun `loadTargetProfile failure updates uiState with loadError`() = runTest {
        // Given
        val errorMessage = "Network timeout"
        coEvery { userRepository.getUserProfile(targetUserId) } throws Exception(errorMessage)

        // When
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoadingData)
        assertNotNull(state.loadError)
        assertTrue(state.loadError!!.contains(errorMessage))
    }

    @Test
    fun `submitRating with valid score calls ratingRepository submitRating`() = runTest {
        // Given
        coEvery { rateLimitManager.checkRateLimit(RateLimitManager.ACTION_RATING) } returns RateLimitResult(success = true)
        coEvery { 
            ratingRepository.submitRating(eventId, EventType.OTRO, currentUserId, targetUserId, 5.0, "Great experience")
        } returns Result.success("rating_id_xyz")

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // When
        viewModel.submitRating(5, "Great experience")
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { 
            ratingRepository.submitRating(eventId, EventType.OTRO, currentUserId, targetUserId, 5.0, "Great experience")
        }
        val state = viewModel.uiState.value
        assertTrue(state.submissionState is ResultState.Success)
    }

    @Test
    fun `submitRating with score out of range returns early and does not call repo`() = runTest {
        // Given
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // When
        viewModel.submitRating(0, "Terrible")
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { 
            ratingRepository.submitRating(any(), any(), any(), any(), any(), any())
        }
        val state = viewModel.uiState.value
        assertTrue(state.submissionState is ResultState.Idle)
    }

    @Test
    fun `submitRating when already submitting cancels prior and ignores duplicate calls`() = runTest {
        // Given
        coEvery { rateLimitManager.checkRateLimit(any()) } returns RateLimitResult(success = true)
        coEvery { ratingRepository.submitRating(any(), any(), any(), any(), any(), any()) } returns Result.success("id")

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // When
        viewModel.submitRating(4, "Nice")
        viewModel.submitRating(5, "Perfect")
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { 
            ratingRepository.submitRating(eventId, EventType.OTRO, currentUserId, targetUserId, 5.0, "Perfect")
        }
    }
}
