package com.sliide.kmp.domain.repository

import com.sliide.kmp.domain.error.AppResult
import com.sliide.kmp.domain.model.NewUser
import com.sliide.kmp.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the user directory.
 *
 * [observeUsers] is cache-backed and therefore always emits, online or not; the network is
 * only ever consulted through the explicit [refresh] / [createUser] / [finaliseDeletion]
 * calls, which report failure instead of throwing.
 */
interface UserRepository {
    /** Cached users, newest first, excluding any awaiting an undo window. */
    fun observeUsers(): Flow<List<User>>

    /** True once a sync has ever completed, so the UI can distinguish "empty" from "cold". */
    suspend fun hasCachedData(): Boolean

    /** Pulls the last page of the remote directory and merges it into the cache. */
    suspend fun refresh(): AppResult<Unit>

    /** Creates a user remotely and inserts it at the top of the cache. */
    suspend fun createUser(newUser: NewUser): AppResult<User>

    /** Hides a user locally and opens the undo window. No network call yet. */
    suspend fun markPendingDeletion(userId: Long)

    /** Cancels a pending deletion, restoring the user to the list. */
    suspend fun undoPendingDeletion(userId: Long)

    /** Commits a pending deletion: DELETE remotely, then drop the local row. */
    suspend fun finaliseDeletion(userId: Long): AppResult<Unit>

    /**
     * Restores every deletion whose undo window never closed - the app was killed before the
     * DELETE was sent, so as far as the server is concerned it never happened.
     */
    suspend fun restoreAbandonedDeletions()
}
