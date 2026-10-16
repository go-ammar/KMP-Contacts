package com.roster.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roster.domain.error.AppResult
import com.roster.domain.model.User
import com.roster.domain.model.UserStatus
import com.roster.domain.time.RelativeTimeFormatter
import com.roster.domain.time.periodicTicker
import com.roster.domain.usecase.FinaliseUserDeletionUseCase
import com.roster.domain.usecase.HasCachedUsersUseCase
import com.roster.domain.usecase.MarkUserPendingDeletionUseCase
import com.roster.domain.usecase.ObserveUsersUseCase
import com.roster.domain.usecase.RefreshUsersUseCase
import com.roster.domain.usecase.RestoreAbandonedDeletionsUseCase
import com.roster.domain.usecase.UndoUserDeletionUseCase
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * MVVM: the view calls plain methods on this class and renders [uiState]. There is no intent
 * bus and no reducer - each method mutates the one state holder directly, which keeps the
 * call sites readable and the tests free of event plumbing.
 *
 * Lives in `shared`, so Android and iOS get identical behaviour and one set of unit tests.
 */
class UserListViewModel(
    private val observeUsers: ObserveUsersUseCase,
    private val refreshUsers: RefreshUsersUseCase,
    private val hasCachedUsers: HasCachedUsersUseCase,
    private val markPendingDeletion: MarkUserPendingDeletionUseCase,
    private val undoDeletion: UndoUserDeletionUseCase,
    private val finaliseDeletion: FinaliseUserDeletionUseCase,
    private val restoreAbandonedDeletions: RestoreAbandonedDeletionsUseCase,
    private val relativeTimeFormatter: RelativeTimeFormatter,
    private val relativeTimeTicker: Flow<Unit> = periodicTicker(RELATIVE_TIME_TICK),
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserListUiState())
    val uiState: StateFlow<UserListUiState> = _uiState.asStateFlow()

    private val _events =
        MutableSharedFlow<UserListEvent>(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: Flow<UserListEvent> = _events.asSharedFlow()

    /** Holds the in-flight undo countdown so a second delete can pre-empt it. */
    private var undoJob: Job? = null

    private var domainUsers: List<User> = emptyList()

    /**
     * A floor on how long the skeleton stays up. A sub-second response would otherwise flash
     * the shimmer for a few frames, which reads as a glitch rather than as loading. Started
     * eagerly so the clock runs from construction, and awaited only on the network path -
     * cached data is real content and ends the skeleton at once.
     */
    private val skeletonFloor: Deferred<Unit> =
        viewModelScope.async { delay(MINIMUM_SKELETON_DURATION) }

    init {
        viewModelScope.launch { restoreAbandonedDeletions() }
        observeCache()
        startRelativeTimeTicker()
        refresh(isUserInitiated = false)
    }

    private fun observeCache() {
        viewModelScope.launch {
            observeUsers().collect { users ->
                domainUsers = users
                _uiState.update { state ->
                    state.copy(
                        users = users.map(::toUiModel),
                        // A cached hit ends the shimmer even if the network is still in flight.
                        isInitialLoading = if (users.isNotEmpty()) false else state.isInitialLoading,
                        blockingError = if (users.isNotEmpty()) null else state.blockingError,
                        selectedUserId = state.selectedUserId?.takeIf { id -> users.any { it.id == id } },
                    )
                }
            }
        }
    }

    /**
     * "5 minutes ago" has to become "6 minutes ago" without anything else changing, so the
     * rows are re-derived on a slow tick rather than only when the cache emits.
     */
    private fun startRelativeTimeTicker() {
        viewModelScope.launch {
            relativeTimeTicker.collect {
                if (domainUsers.isEmpty()) {
                    return@collect
                }
                _uiState.update { it.copy(users = domainUsers.map(::toUiModel)) }
            }
        }
    }

    fun refresh(isUserInitiated: Boolean = true) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = isUserInitiated,
                    staleDataWarning = null,
                    blockingError = if (isUserInitiated) null else it.blockingError,
                )
            }

            val result = refreshUsers()
            if (_uiState.value.isInitialLoading) {
                skeletonFloor.await()
            }

            when (result) {
                is AppResult.Success ->
                    _uiState.update {
                        it.copy(isRefreshing = false, isInitialLoading = false, blockingError = null)
                    }

                is AppResult.Failure -> {
                    // Stale data beats an error screen: only block the whole screen when the
                    // cache has nothing to show.
                    val hasCache = hasCachedUsers()
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isInitialLoading = false,
                            blockingError = result.error.takeUnless { _ -> hasCache },
                            staleDataWarning = result.error.takeIf { _ -> hasCache },
                        )
                    }
                }
            }
        }
    }

    fun onUserSelected(userId: Long) {
        _uiState.update { it.copy(selectedUserId = userId.takeIf { id -> id != it.selectedUserId }) }
    }

    fun onDetailDismissed() {
        _uiState.update { it.copy(selectedUserId = null) }
    }

    fun onDeleteRequested(userId: Long) {
        val user = _uiState.value.users.firstOrNull { it.id == userId } ?: return
        _uiState.update { it.copy(confirmingDeletionFor = user) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(confirmingDeletionFor = null) }
    }

    /**
     * Hides the row and opens the undo window. The DELETE is deliberately deferred until the
     * window closes: confirming and then un-deleting would otherwise need a re-POST, which
     * would hand the user a different id than the one they just "restored".
     */
    fun onDeleteConfirmed() {
        val target = _uiState.value.confirmingDeletionFor ?: return

        viewModelScope.launch {
            // Only one undo window at a time: commit the previous one before opening a new one.
            commitPendingDeletionNow()

            markPendingDeletion(target.id)
            _uiState.update {
                it.copy(
                    confirmingDeletionFor = null,
                    selectedUserId = it.selectedUserId?.takeIf { id -> id != target.id },
                    pendingDeletion =
                        PendingDeletionUiModel(
                            user = target,
                            windowMillis = UNDO_WINDOW.inWholeMilliseconds,
                        ),
                )
            }

            // The UI animates the ring over the same window; the state does not need to
            // tick, so nothing recomposes until the window actually closes.
            undoJob =
                launch {
                    delay(UNDO_WINDOW)
                    commitDeletion(target)
                }
        }
    }

    fun onUndoDeletion() {
        val pending = _uiState.value.pendingDeletion ?: return
        undoJob?.cancel()
        undoJob = null
        _uiState.update { it.copy(pendingDeletion = null) }
        viewModelScope.launch { undoDeletion(pending.user.id) }
    }

    fun onStaleWarningDismissed() {
        _uiState.update { it.copy(staleDataWarning = null) }
    }

    /**
     * Finishes any open undo window immediately, e.g. because another delete is starting.
     * The commit is launched rather than awaited so the new undo window opens without
     * waiting on the previous DELETE round-trip.
     */
    private fun commitPendingDeletionNow() {
        val pending = _uiState.value.pendingDeletion ?: return
        undoJob?.cancel()
        undoJob = null
        _uiState.update { it.copy(pendingDeletion = null) }
        viewModelScope.launch { commitDeletion(pending.user) }
    }

    private suspend fun commitDeletion(target: UserUiModel) {
        _uiState.update { state ->
            if (state.pendingDeletion?.user?.id == target.id) state.copy(pendingDeletion = null) else state
        }
        when (val result = finaliseDeletion(target.id)) {
            is AppResult.Success -> Unit
            is AppResult.Failure ->
                _events.emit(
                    UserListEvent.DeleteFailed(userName = target.name, error = result.error),
                )
        }
    }

    private fun toUiModel(user: User): UserUiModel =
        UserUiModel(
            id = user.id,
            name = user.name,
            email = user.email,
            initials = user.initials,
            relativeTimestamp = relativeTimeFormatter.format(user.firstSeenAt),
            isActive = user.status == UserStatus.Active,
            genderLabel = user.gender.wireValue.replaceFirstChar { it.uppercaseChar() },
        )

    private companion object {
        val UNDO_WINDOW = 5.seconds
        val RELATIVE_TIME_TICK = 30.seconds
        val MINIMUM_SKELETON_DURATION = 500.milliseconds
    }
}
