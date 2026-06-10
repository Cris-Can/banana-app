package com.eventos.banana.ui.event

import com.eventos.banana.MainDispatcherRule
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.data.repository.SubscriptionRepository
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.usecase.event.CreateEventUseCase
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateEventViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var eventRepository: EventRepository
    private lateinit var createEventUseCase: CreateEventUseCase
    private lateinit var subscriptionRepository: SubscriptionRepository

    @org.junit.After
    fun tearDown() {
        unmockkAll()
    }

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        eventRepository = mockk()
        createEventUseCase = mockk()
        subscriptionRepository = mockk()

        // Default mock behaviors
        coEvery { subscriptionRepository.getUserLimitStats(any()) } returns null
        coEvery { subscriptionRepository.getDebugStats(any()) } returns "Mock limits"
    }

    private fun createViewModel(): CreateEventViewModel {
        return CreateEventViewModel(
            repository = eventRepository,
            createEventUseCase = createEventUseCase,
            subscriptionRepository = subscriptionRepository
        )
    }

    @Test
    fun `createEvent with valid data calls usecase and shows success`() = runTest {
        // Given
        val validEvent = Event(
            id = "event_123",
            creatorId = "creator_abc",
            title = "Banana Fiesta",
            startAt = 1000L,
            endAt = 2000L
        )
        coEvery { createEventUseCase(validEvent, any(), any()) } returns Result.success(Unit)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // When
        viewModel.createEvent(validEvent, null)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { createEventUseCase(validEvent, null) }
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.success)
        assertNull(state.errorMessage)
    }

    @Test
    fun `createEvent with empty title sets validation error and does not call usecase`() = runTest {
        // Given
        val invalidEvent = Event(
            id = "event_123",
            creatorId = "creator_abc",
            title = "   ", // Empty title
            startAt = 1000L,
            endAt = 2000L
        )

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // When
        viewModel.createEvent(invalidEvent, null)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { createEventUseCase(any(), any(), any()) }
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.success)
        assertEquals("El título no puede estar vacío", state.errorMessage)
    }

    @Test
    fun `createEvent when rate limited sets LIMIT_REACHED error and fetches debug stats`() = runTest {
        // Given
        val validEvent = Event(
            id = "event_123",
            creatorId = "creator_abc",
            title = "Banana Fiesta",
            startAt = 1000L,
            endAt = 2000L
        )
        coEvery { createEventUseCase(validEvent, null) } returns Result.failure(Exception("LIMIT_REACHED"))

        val mockStats = mockk<SubscriptionRepository.UserLimitStats>()
        coEvery { subscriptionRepository.getDebugStats("creator_abc") } returns "Limits reached details"
        coEvery { subscriptionRepository.getUserLimitStats("creator_abc") } returns mockStats

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // When
        viewModel.createEvent(validEvent, null)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { createEventUseCase(validEvent, null) }
        coVerify(exactly = 1) { subscriptionRepository.getDebugStats("creator_abc") }
        coVerify(exactly = 1) { subscriptionRepository.getUserLimitStats("creator_abc") }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.success)
        assertEquals("LIMIT_REACHED", state.errorMessage)
        assertEquals("Limits reached details", viewModel.limitDebugInfo.value)
        assertEquals(mockStats, viewModel.userLimitStats.value)
    }

    @Test
    fun `createEventUseCase fails with generic error sets errorMessage`() = runTest {
        // Given
        val validEvent = Event(
            id = "event_123",
            creatorId = "creator_abc",
            title = "Banana Fiesta",
            startAt = 1000L,
            endAt = 2000L
        )
        coEvery { createEventUseCase(validEvent, null) } returns Result.failure(Exception("Firestore write failed"))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // When
        viewModel.createEvent(validEvent, null)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { createEventUseCase(validEvent, null) }
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.success)
        assertEquals("Firestore write failed", state.errorMessage)
    }

    @Test
    fun `image selection updates formState selectedImageUri`() = runTest {
        // Given
        val mockUri = mockk<android.net.Uri>()
        val viewModel = createViewModel()

        // When
        viewModel.updateSelectedImageUri(mockUri)
        testScheduler.advanceUntilIdle()

        // Then
        assertEquals(mockUri, viewModel.formState.value.selectedImageUri)
    }
}
