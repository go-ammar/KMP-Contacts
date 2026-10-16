package com.roster.presentation

import app.cash.turbine.test
import com.roster.domain.error.AppError
import com.roster.domain.error.AppResult
import com.roster.domain.time.RelativeTimeFormatter
import com.roster.domain.usecase.FinaliseUserDeletionUseCase
import com.roster.domain.usecase.HasCachedUsersUseCase
import com.roster.domain.usecase.MarkUserPendingDeletionUseCase
import com.roster.domain.usecase.ObserveUsersUseCase
import com.roster.domain.usecase.RefreshUsersUseCase
import com.roster.domain.usecase.RestoreAbandonedDeletionsUseCase
import com.roster.domain.usecase.UndoUserDeletionUseCase
import com.roster.fake.FakeUserRepository
import com.roster.fake.user
import com.roster.presentation.users.UserListEvent
import com.roster.presentation.users.UserListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UserListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val fixedClock =
        object : Clock {
            override fun now(): Instant = fixedNow
        }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeUserRepository,
        ticker: Flow<Unit> = emptyFlow(),
    ) = UserListViewModel(
        observeUsers = ObserveUsersUseCase(repository),
        refreshUsers = RefreshUsersUseCase(repository),
        hasCachedUsers = HasCachedUsersUseCase(repository),
        markPendingDeletion = MarkUserPendingDeletionUseCase(repository),
        undoDeletion = UndoUserDeletionUseCase(repository),
        finaliseDeletion = FinaliseUserDeletionUseCase(repository),
        restoreAbandonedDeletions = RestoreAbandonedDeletionsUseCase(repository),
        relativeTimeFormatter = RelativeTimeFormatter(fixedClock),
        relativeTimeTicker = ticker,
    )

    @Test
    fun `starts in the loading state and clears it once the cache emits`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)

            assertTrue(viewModel.uiState.value.isInitialLoading)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.users.size)
            assertEquals(false, state.isInitialLoading)
            assertNull(state.blockingError)
        }

    @Test
    fun `a fast network response still holds the skeleton long enough not to flash`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository()
            val viewModel = this@UserListViewModelTest.viewModel(repository)

            // Refresh has already returned here, but the floor has not elapsed.
            advanceTimeBy(200.milliseconds)
            assertTrue(
                viewModel.uiState.value.isInitialLoading,
                "the skeleton should not disappear within a few frames of appearing",
            )

            advanceTimeBy(400.milliseconds)
            assertFalse(viewModel.uiState.value.isInitialLoading)
        }

    @Test
    fun `cached data ends the skeleton immediately rather than waiting out the floor`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)

            // Well inside the minimum skeleton window: real content must win over the floor.
            advanceTimeBy(50.milliseconds)

            assertFalse(viewModel.uiState.value.isInitialLoading)
            assertEquals(1, viewModel.uiState.value.users.size)
        }

    @Test
    fun `refreshes once on construction without being asked`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository()
            viewModel(repository)

            advanceUntilIdle()

            assertEquals(1, repository.refreshCount)
        }

    @Test
    fun `clears deletions abandoned by a previous process on construction`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository()
            viewModel(repository)

            advanceUntilIdle()

            assertEquals(1, repository.restoreAbandonedCount)
        }

    @Test
    fun `a failed refresh with nothing cached blocks the whole screen`() =
        runTest(dispatcher) {
            val repository =
                FakeUserRepository().apply {
                    refreshResult = AppResult.Failure(AppError.NoInternet)
                }
            val viewModel = this@UserListViewModelTest.viewModel(repository)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(AppError.NoInternet, state.blockingError)
            assertNull(state.staleDataWarning)
        }

    @Test
    fun `a failed refresh with cached data keeps the list and warns instead`() =
        runTest(dispatcher) {
            val repository =
                FakeUserRepository(listOf(user(1), user(2))).apply {
                    refreshResult = AppResult.Failure(AppError.NoInternet)
                }
            val viewModel = this@UserListViewModelTest.viewModel(repository)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.blockingError)
            assertEquals(AppError.NoInternet, state.staleDataWarning)
            assertEquals(2, state.users.size)
        }

    @Test
    fun `confirming a delete hides the row immediately and defers the API call`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1), user(2)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onDeleteRequested(1)
            viewModel.onDeleteConfirmed()
            advanceTimeBy(100)

            val state = viewModel.uiState.value
            assertEquals(listOf(2L), state.users.map { it.id })
            assertEquals(1L, state.pendingDeletion?.user?.id)
            assertTrue(repository.finalisedIds.isEmpty(), "the DELETE must wait for the undo window")
        }

    @Test
    fun `undo restores the row and never calls the API`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1), user(2)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onDeleteRequested(1)
            viewModel.onDeleteConfirmed()
            advanceTimeBy(2.seconds)
            viewModel.onUndoDeletion()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(1L, 2L), state.users.map { it.id }.sorted())
            assertNull(state.pendingDeletion)
            assertEquals(listOf(1L), repository.undoneIds)
            assertTrue(repository.finalisedIds.isEmpty())
        }

    @Test
    fun `letting the undo window close commits the deletion`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1), user(2)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onDeleteRequested(1)
            viewModel.onDeleteConfirmed()
            advanceUntilIdle()

            assertEquals(listOf(1L), repository.finalisedIds)
            assertNull(viewModel.uiState.value.pendingDeletion)
            assertEquals(
                listOf(2L),
                viewModel.uiState.value.users
                    .map { it.id },
            )
        }

    @Test
    fun `the undo window publishes its own length for the progress animation to follow`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onDeleteRequested(1)
            viewModel.onDeleteConfirmed()
            advanceTimeBy(100)

            assertEquals(
                5_000L,
                viewModel.uiState.value.pendingDeletion
                    ?.windowMillis,
            )
        }

    @Test
    fun `the window stays open for its full length and not a moment less`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onDeleteRequested(1)
            viewModel.onDeleteConfirmed()

            advanceTimeBy(4.seconds)
            assertEquals(
                1L,
                viewModel.uiState.value.pendingDeletion
                    ?.user
                    ?.id,
                "still undoable at 4s",
            )
            assertTrue(repository.finalisedIds.isEmpty())

            advanceTimeBy(1.seconds + 100.milliseconds)
            assertNull(viewModel.uiState.value.pendingDeletion, "the snackbar leaves as the ring fills")
            assertEquals(listOf(1L), repository.finalisedIds)
        }

    @Test
    fun `starting a second delete commits the first one straight away`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1), user(2)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onDeleteRequested(1)
            viewModel.onDeleteConfirmed()
            advanceTimeBy(1.seconds)

            viewModel.onDeleteRequested(2)
            viewModel.onDeleteConfirmed()
            advanceTimeBy(100)

            assertEquals(listOf(1L), repository.finalisedIds, "the first delete should already be committed")
            assertEquals(
                2L,
                viewModel.uiState.value.pendingDeletion
                    ?.user
                    ?.id,
            )
        }

    @Test
    fun `a failed delete reports which user failed and why`() =
        runTest(dispatcher) {
            val repository =
                FakeUserRepository(listOf(user(1, name = "Ada Lovelace"))).apply {
                    deleteResult = AppResult.Failure(AppError.NoInternet)
                }
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onDeleteRequested(1)
                viewModel.onDeleteConfirmed()
                advanceUntilIdle()

                val event = awaitItem()
                assertTrue(event is UserListEvent.DeleteFailed)
                // Asserts the typed failure, not the wording - copy lives in the UI layer.
                assertEquals("Ada Lovelace", event.userName)
                assertEquals(AppError.NoInternet, event.error)
            }
        }

    @Test
    fun `undo after the window has already closed is a no-op`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1), user(2)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onDeleteRequested(1)
            viewModel.onDeleteConfirmed()
            advanceUntilIdle()

            // The pill has gone and the DELETE has committed; a late tap must not resurrect
            // a user the server no longer has.
            viewModel.onUndoDeletion()
            advanceUntilIdle()

            assertEquals(listOf(1L), repository.finalisedIds)
            assertTrue(repository.undoneIds.isEmpty())
            assertEquals(
                listOf(2L),
                viewModel.uiState.value.users
                    .map { it.id },
            )
        }

    @Test
    fun `a refresh during an undo window does not bring the hidden user back`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1), user(2)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onDeleteRequested(1)
            viewModel.onDeleteConfirmed()
            advanceTimeBy(1.seconds)

            viewModel.refresh()
            advanceTimeBy(500.milliseconds)

            assertEquals(
                listOf(2L),
                viewModel.uiState.value.users
                    .map { it.id },
            )
            assertEquals(
                1L,
                viewModel.uiState.value.pendingDeletion
                    ?.user
                    ?.id,
            )
        }

    @Test
    fun `dismissing the confirmation leaves the user in place`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onDeleteRequested(1)
            viewModel.onDeleteDismissed()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.confirmingDeletionFor)
            assertEquals(
                listOf(1L),
                viewModel.uiState.value.users
                    .map { it.id },
            )
            assertTrue(repository.finalisedIds.isEmpty())
        }

    @Test
    fun `selecting the same user twice clears the selection`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onUserSelected(1)
            assertEquals(1L, viewModel.uiState.value.selectedUserId)

            viewModel.onUserSelected(1)
            assertNull(viewModel.uiState.value.selectedUserId)
        }

    @Test
    fun `a selection pointing at a deleted user is dropped when the cache changes`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1), user(2)))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            viewModel.onUserSelected(1)
            repository.emit(listOf(user(2)))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedUserId)
        }

    @Test
    fun `relative timestamps are recomputed on each tick`() =
        runTest(dispatcher) {
            val repository =
                FakeUserRepository(
                    listOf(user(1, firstSeenAtMillis = fixedNow.toEpochMilliseconds() - 5 * 60 * 1000)),
                )
            val viewModel = viewModel(repository, ticker = flowOf(Unit))
            advanceUntilIdle()

            assertEquals(
                "5 minutes ago",
                viewModel.uiState.value.users
                    .single()
                    .relativeTimestamp,
            )
        }

    @Test
    fun `rows carry the initials and status the list needs`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository(listOf(user(1, name = "Ada Lovelace")))
            val viewModel = this@UserListViewModelTest.viewModel(repository)
            advanceUntilIdle()

            val row =
                viewModel.uiState.value.users
                    .single()
            assertEquals("AL", row.initials)
            assertEquals("Active", row.statusLabel)
            assertEquals("Female", row.genderLabel)
        }
}
