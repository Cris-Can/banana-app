package com.eventos.banana.ui.rating

import com.eventos.banana.MainDispatcherRule
import com.eventos.banana.data.repository.EncounterRepository
import com.eventos.banana.data.repository.RatingRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.EventType
import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.domain.usecase.rating.GetPendingRatingsUseCase
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RatingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var userRepository: UserRepository
    private lateinit var ratingRepository: RatingRepository
    private lateinit var encounterRepo: EncounterRepository
    private lateinit var getPendingRatingsUseCase: GetPendingRatingsUseCase

    private val eventId = "test_event_id"
    private val eventType = EventType.SOCIAL
    private val currentUserId = "current_user_1"
    private val participantIds = listOf("current_user_1", "user_2", "user_3")

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        userRepository = mockk()
        ratingRepository = mockk()
        encounterRepo = mockk()
        getPendingRatingsUseCase = mockk()

        // Default mock behaviors to allow successful initialization
        coEvery { userRepository.getUsers(any()) } returns emptyList()
        coEvery { encounterRepo.getEncountersForUser(any(), any()) } returns Result.success(emptyList())
        coEvery { encounterRepo.shouldEnforceEncounters(any()) } returns Result.success(false)
        coEvery { ratingRepository.hasSkippedRating(any(), any()) } returns Result.success(false)
        coEvery { ratingRepository.getAlreadyRatedUsers(any(), any()) } returns Result.success(emptySet())
    }

    private fun createViewModel(): RatingViewModel {
        return RatingViewModel(
            eventId = eventId,
            eventType = eventType,
            currentUserId = currentUserId,
            participantIds = participantIds,
            userRepository = userRepository,
            ratingRepository = ratingRepository,
            encounterRepo = encounterRepo,
            getPendingRatingsUseCase = getPendingRatingsUseCase
        )
    }

    @Test
    fun `loadUsersToRate success updates uiState with pending users`() = runTest {
        // Given
        val user2Profile = UserProfile(uid = "user_2", nickname = "Nickname 2")
        val user3Profile = UserProfile(uid = "user_3", nickname = "Nickname 3")
        
        coEvery { userRepository.getUsers(listOf("user_2", "user_3")) } returns listOf(user2Profile, user3Profile)
        coEvery { encounterRepo.shouldEnforceEncounters(eventId) } returns Result.success(true)
        coEvery { encounterRepo.getEncountersForUser(eventId, currentUserId) } returns Result.success(listOf("user_2"))
        coEvery { ratingRepository.hasSkippedRating(currentUserId, eventId) } returns Result.success(false)
        coEvery { ratingRepository.getAlreadyRatedUsers(eventId, currentUserId) } returns Result.success(emptySet())

        // When
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        println("TEST DEBUG STATE: $state")
        assertFalse(state.isLoading)
        assertEquals(1, state.usersToRate.size)
        assertEquals("user_2", state.usersToRate[0].uid)
        assertTrue(state.alreadyRated.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadUsersToRate failure updates uiState with errorMessage`() = runTest {
        // Given
        val errorMessage = "Database is unreachable"
        coEvery { userRepository.getUsers(any()) } throws Exception(errorMessage)

        // When
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.usersToRate.isEmpty())
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains(errorMessage))
    }

    @Test
    fun `submitRating success removes user from usersToRate and updates alreadyRated`() = runTest {
        // Given
        val user2Profile = UserProfile(uid = "user_2", nickname = "Nickname 2")
        coEvery { userRepository.getUsers(any()) } returns listOf(user2Profile)
        coEvery { ratingRepository.submitRating(eventId, eventType, currentUserId, "user_2", 5.0, "Great guy") } returns Result.success("rating_id_abc")

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()
        
        // Before submit
        assertEquals(1, viewModel.uiState.value.usersToRate.size)
        assertTrue(viewModel.uiState.value.alreadyRated.isEmpty())

        // When
        viewModel.submitRating("user_2", 5, "Great guy")
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.usersToRate.isEmpty())
        assertEquals(setOf("user_2"), state.alreadyRated)
        assertEquals("Puntuación enviada", state.successMessage)
        assertNull(state.errorMessage)
    }

    @Test
    fun `submitRating failure sets errorMessage and keeps usersToRate intact`() = runTest {
        // Given
        val user2Profile = UserProfile(uid = "user_2", nickname = "Nickname 2")
        coEvery { userRepository.getUsers(any()) } returns listOf(user2Profile)
        coEvery { ratingRepository.submitRating(any(), any(), any(), any(), any(), any()) } returns Result.failure(Exception("Submission limit exceeded"))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // When
        viewModel.submitRating("user_2", 4, "Cool")
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.usersToRate.size)
        assertEquals("user_2", state.usersToRate[0].uid)
        assertTrue(state.alreadyRated.isEmpty())
        assertEquals("Submission limit exceeded", state.errorMessage)
    }

    @Test
    fun `skipRating success marks isSkipped and clears usersToRate`() = runTest {
        // Given
        val user2Profile = UserProfile(uid = "user_2", nickname = "Nickname 2")
        coEvery { userRepository.getUsers(any()) } returns listOf(user2Profile)
        coEvery { ratingRepository.skipRating(currentUserId, eventId) } returns Result.success(Unit)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // When
        viewModel.skipRating()
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isSkipped)
        assertEquals("Recordatorios desactivados para este evento", state.successMessage)
    }
}
