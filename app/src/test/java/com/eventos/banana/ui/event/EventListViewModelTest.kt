package com.eventos.banana.ui.event

import com.eventos.banana.MainDispatcherRule
import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.data.repository.EventModerationRepository
import com.eventos.banana.data.repository.MainFeedRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventListUiState
import com.eventos.banana.domain.model.EventStatus
import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var eventRepository: EventRepository
    private lateinit var eventModerationRepository: EventModerationRepository
    private lateinit var mainFeedRepository: MainFeedRepository
    private lateinit var userRepository: UserRepository
    private lateinit var authRepository: AuthRepository

    private val currentUserId = "current_user_1"
    private val mockDocSnapshot = mockk<DocumentSnapshot>()
    private val mockDocSnapshot2 = mockk<DocumentSnapshot>()

    private val mockEvent1 = Event(
        id = "event_1",
        creatorId = "creator_1",
        title = "Banana Social",
        status = EventStatus.OPEN,
        startAt = System.currentTimeMillis() + 100000L,
        endAt = System.currentTimeMillis() + 500000L
    )

    private val mockEvent2 = Event(
        id = "event_2",
        creatorId = "creator_2",
        title = "Banana Sports",
        status = EventStatus.OPEN,
        startAt = System.currentTimeMillis() + 100000L,
        endAt = System.currentTimeMillis() + 500000L
    )

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

        mockkStatic(android.location.Location::class)
        every {
            android.location.Location.distanceBetween(any(), any(), any(), any(), any())
        } answers {
            val results = arg<FloatArray>(4)
            results[0] = 500f // 500 meters, inside any search radius
        }

        eventRepository = mockk()
        eventModerationRepository = mockk()
        mainFeedRepository = mockk()
        userRepository = mockk()
        authRepository = mockk()

        // Default mock behaviors
        coEvery { authRepository.currentUid() } returns currentUserId
        coEvery { userRepository.getUserProfile(currentUserId) } returns UserProfile(uid = currentUserId, searchRadiusKm = 20)
        coEvery { eventModerationRepository.markFinishedEventsAsRatable() } returns Result.success(0)
        coEvery { userRepository.getUsers(any()) } returns emptyList()
        coEvery { userRepository.updateSearchRadius(any(), any()) } returns Unit

        // Default fetch return empty list
        coEvery {
            mainFeedRepository.fetchEventsBatch(
                geohashPrefix = any(),
                centerLat = any(),
                centerLng = any(),
                commune = any(),
                region = any(),
                limit = any(),
                lastSnapshot = any(),
                radiusKm = any(),
                seenEventIds = any()
            )
        } returns Result.success(Pair(emptyList(), null))
    }

    private fun createViewModel(): EventListViewModel {
        return EventListViewModel(
            repository = eventRepository,
            eventModerationRepository = eventModerationRepository,
            mainFeedRepository = mainFeedRepository,
            userRepository = userRepository,
            authRepository = authRepository
        )
    }

    private suspend fun awaitSuccessState(viewModel: EventListViewModel, timeoutMs: Long = 3000): EventListUiState.Success {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val state = viewModel.uiState.value
            if (state is EventListUiState.Success) {
                return state
            }
            kotlinx.coroutines.yield()
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for EventListUiState.Success. Current state: ${viewModel.uiState.value}")
    }

    private suspend fun awaitEventsSize(viewModel: EventListViewModel, expectedSize: Int, timeoutMs: Long = 3000): EventListUiState.Success {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val state = viewModel.uiState.value
            if (state is EventListUiState.Success && state.events.size == expectedSize) {
                return state
            }
            kotlinx.coroutines.yield()
            Thread.sleep(10)
        }
        val currentState = viewModel.uiState.value
        val actualSize = if (currentState is EventListUiState.Success) currentState.events.size else "not success state"
        throw AssertionError("Timed out waiting for events size $expectedSize. Current state events size: $actualSize. State: $currentState")
    }

    @Test
    fun `initial load transitions from Loading to Success with events`() = runTest {
        // Given
        coEvery {
            mainFeedRepository.fetchEventsBatch(
                geohashPrefix = any(),
                centerLat = any(),
                centerLng = any(),
                commune = any(),
                region = any(),
                limit = any(),
                lastSnapshot = null,
                radiusKm = any(),
                seenEventIds = any()
            )
        } returns Result.success(Pair(listOf(mockEvent1), mockDocSnapshot))

        // When
        val viewModel = createViewModel()
        
        // Before subscribing / coroutines running, it's loading
        assertTrue(viewModel.uiState.value is EventListUiState.Loading)

        // Subscribe inside backgroundScope to activate StateFlow combine operations
        backgroundScope.launch { viewModel.uiState.collect {} }
        val successState = awaitSuccessState(viewModel)

        // Then
        assertEquals(1, successState.events.size)
        assertEquals("event_1", successState.events[0].id)
    }

    @Test
    fun `loadMore appends events to existing list`() = runTest {
        // Given
        coEvery {
            mainFeedRepository.fetchEventsBatch(
                geohashPrefix = any(),
                centerLat = any(),
                centerLng = any(),
                commune = any(),
                region = any(),
                limit = any(),
                lastSnapshot = null,
                radiusKm = any(),
                seenEventIds = any()
            )
        } returns Result.success(Pair(listOf(mockEvent1), mockDocSnapshot))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        awaitSuccessState(viewModel)

        // Mock next batch fetch for loadMore
        coEvery {
            mainFeedRepository.fetchEventsBatch(
                geohashPrefix = any(),
                centerLat = any(),
                centerLng = any(),
                commune = any(),
                region = any(),
                limit = any(),
                lastSnapshot = any(),
                radiusKm = any(),
                seenEventIds = any()
            )
        } returns Result.success(Pair(listOf(mockEvent2), mockDocSnapshot2))

        // When
        viewModel.loadMore()
        val successState = awaitEventsSize(viewModel, 2)

        // Then
        assertEquals("event_1", successState.events[0].id)
        assertEquals("event_2", successState.events[1].id)
    }

    @Test
    fun `loadMore when isLastPage returns early and does not fetch again`() = runTest {
        // Given
        coEvery {
            mainFeedRepository.fetchEventsBatch(
                geohashPrefix = any(),
                centerLat = any(),
                centerLng = any(),
                commune = any(),
                region = any(),
                limit = any(),
                lastSnapshot = null,
                radiusKm = any(),
                seenEventIds = any()
            )
        } returns Result.success(Pair(listOf(mockEvent1), mockDocSnapshot))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        awaitSuccessState(viewModel)

        // Now loadMore once. It returns 1 event (mockEvent2), isLastPage becomes true.
        coEvery {
            mainFeedRepository.fetchEventsBatch(
                geohashPrefix = any(),
                centerLat = any(),
                centerLng = any(),
                commune = any(),
                region = any(),
                limit = any(),
                lastSnapshot = any(),
                radiusKm = any(),
                seenEventIds = any()
            )
        } returns Result.success(Pair(listOf(mockEvent2), mockDocSnapshot2))

        viewModel.loadMore()
        val successState = awaitEventsSize(viewModel, 2)
        assertFalse(successState.canLoadMore)

        // Reset verify count
        clearMocks(mainFeedRepository, answers = false)

        // When
        viewModel.loadMore()
        Thread.sleep(100)

        // Then
        // Verify mainFeedRepository was NOT called during the second loadMore
        coVerify(exactly = 0) {
            mainFeedRepository.fetchEventsBatch(
                geohashPrefix = any(),
                centerLat = any(),
                centerLng = any(),
                commune = any(),
                region = any(),
                limit = any(),
                lastSnapshot = any(),
                radiusKm = any(),
                seenEventIds = any()
            )
        }
    }

    @Test
    fun `loadMore fails keeps previous events in success state`() = runTest {
        // Given
        coEvery {
            mainFeedRepository.fetchEventsBatch(
                geohashPrefix = any(),
                centerLat = any(),
                centerLng = any(),
                commune = any(),
                region = any(),
                limit = any(),
                lastSnapshot = null,
                radiusKm = any(),
                seenEventIds = any()
            )
        } returns Result.success(Pair(listOf(mockEvent1), mockDocSnapshot))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        awaitSuccessState(viewModel)

        // Mock failure for loadMore
        coEvery {
            mainFeedRepository.fetchEventsBatch(
                geohashPrefix = any(),
                centerLat = any(),
                centerLng = any(),
                commune = any(),
                region = any(),
                limit = any(),
                lastSnapshot = any(),
                radiusKm = any(),
                seenEventIds = any()
            )
        } returns Result.failure(Exception("Connection lost"))

        // When
        viewModel.loadMore()
        
        // Wait for loadMore coroutine to complete (isLoadingMore returns to false)
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 3000) {
            if (!viewModel.isLoadingMore.value) break
            kotlinx.coroutines.yield()
            Thread.sleep(10)
        }

        // Then
        // UI State remains Success and retains mockEvent1
        val state = viewModel.uiState.value
        assertTrue(state is EventListUiState.Success)
        val successState = state as EventListUiState.Success
        assertEquals(1, successState.events.size)
        assertEquals("event_1", successState.events[0].id)
    }

    @Test
    fun `searchNearLocation with valid location updates center location and geohash in queryParams`() = runTest {
        // Given
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        awaitSuccessState(viewModel)

        // When
        viewModel.searchNearLocation(lat = -33.45, lng = -70.66, radiusKm = 10)
        
        // Wait for queryParams to update!
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 3000) {
            if (viewModel.queryParams.value.geohash != null) break
            kotlinx.coroutines.yield()
            Thread.sleep(10)
        }

        // Then
        val params = viewModel.queryParams.value
        assertNotNull(params.geohash)
        assertEquals(10, params.radiusKm)
        assertEquals(locToExactLocation(viewModel.userLocation.value), -33.45, 0.001)
        assertEquals(locToExactLocationLng(viewModel.userLocation.value), -70.66, 0.001)
    }

    @Test
    fun `updateRadius sets new radius resets pagination and persists radius`() = runTest {
        // Given
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        awaitSuccessState(viewModel)

        // When
        viewModel.updateRadius(30)
        
        // Wait for searchRadiusKm to update!
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 3000) {
            if (viewModel.searchRadiusKm.value == 30) break
            kotlinx.coroutines.yield()
            Thread.sleep(10)
        }

        // Then
        assertEquals(30, viewModel.searchRadiusKm.value)
        coVerify(exactly = 1) { userRepository.updateSearchRadius(currentUserId, 30) }
    }

    // Helper utilities since ExactLocation fields might be internal/private in check
    private fun locToExactLocation(loc: com.eventos.banana.domain.model.ExactLocation?): Double {
        return loc?.latitude ?: 0.0
    }

    private fun locToExactLocationLng(loc: com.eventos.banana.domain.model.ExactLocation?): Double {
        return loc?.longitude ?: 0.0
    }
}
