package com.sliide.kmp.presentation.users

import com.sliide.kmp.domain.error.AppError

/**
 * Everything the list screen renders, as one immutable snapshot. The screen is a pure
 * function of this value - there is no second source of truth in the composables.
 */
data class UserListUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val users: List<UserUiModel> = emptyList(),
    /** Set only when there is nothing cached to fall back on, so the whole screen is an error. */
    val blockingError: AppError? = null,
    /** Set when a refresh failed but stale data is still on screen. */
    val staleDataWarning: AppError? = null,
    val selectedUserId: Long? = null,
    val confirmingDeletionFor: UserUiModel? = null,
    val pendingDeletion: PendingDeletionUiModel? = null,
) {
    val isEmpty: Boolean get() = users.isEmpty() && !isInitialLoading && blockingError == null

    val selectedUser: UserUiModel? get() = users.firstOrNull { it.id == selectedUserId }
}

/** A list row, with the relative timestamp already resolved in shared code. */
data class UserUiModel(
    val id: Long,
    val name: String,
    val email: String,
    val initials: String,
    val relativeTimestamp: String,
    val isActive: Boolean,
    val genderLabel: String,
) {
    val statusLabel: String get() = if (isActive) "Active" else "Inactive"
}

/**
 * The undo window: the row is hidden, the DELETE has not been sent yet.
 *
 * [windowMillis] is how long the window lasts, published so the progress animation is driven
 * by the same number as the timer that commits the deletion rather than a duplicate constant.
 */
data class PendingDeletionUiModel(
    val user: UserUiModel,
    val windowMillis: Long,
)

/**
 * Fire-and-forget signals that must not be replayed on rotation.
 *
 * These carry a typed error rather than a finished sentence: turning an [AppError] into words
 * belongs to the UI layer, and keeping it there is what makes the copy localisable.
 */
sealed interface UserListEvent {
    data class DeleteFailed(
        val userName: String,
        val error: AppError,
    ) : UserListEvent
}
