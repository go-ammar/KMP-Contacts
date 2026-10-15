package com.sliide.kmp.data.repository

import com.sliide.kmp.data.local.UserLocalDataSource
import com.sliide.kmp.data.mapper.toDomain
import com.sliide.kmp.data.mapper.toRequest
import com.sliide.kmp.data.remote.UserRemoteDataSource
import com.sliide.kmp.domain.error.AppResult
import com.sliide.kmp.domain.error.map
import com.sliide.kmp.domain.model.NewUser
import com.sliide.kmp.domain.model.User
import com.sliide.kmp.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

/**
 * Offline-first: the cache is the only thing the UI reads from, and the network is a way of
 * updating it. A failed refresh therefore degrades to stale data plus an error banner rather
 * than to an empty screen.
 */
class UserRepositoryImpl(
    private val remote: UserRemoteDataSource,
    private val local: UserLocalDataSource,
    private val clock: Clock = Clock.System,
) : UserRepository {
    override fun observeUsers(): Flow<List<User>> = local.observeVisibleUsers()

    override suspend fun hasCachedData(): Boolean = local.countAll() > 0

    override suspend fun refresh(): AppResult<Unit> {
        val now = clock.now()
        return remote
            .fetchLastPageOfUsers()
            .map { dtos -> dtos.map { it.toDomain(firstSeenAt = now) } }
            .let { result ->
                when (result) {
                    is AppResult.Success -> {
                        local.upsertAll(result.value)
                        AppResult.Success(Unit)
                    }

                    is AppResult.Failure -> result
                }
            }
    }

    override suspend fun createUser(newUser: NewUser): AppResult<User> =
        remote
            .createUser(newUser.toRequest())
            .map { dto -> dto.toDomain(firstSeenAt = clock.now()) }
            .also { result ->
                // Cached immediately so the new row is at the top of the list before the
                // caller's coroutine resumes - no refresh round-trip in between.
                if (result is AppResult.Success) {
                    local.upsert(result.value)
                }
            }

    override suspend fun restoreAbandonedDeletions() {
        local.clearAllPendingDeletes()
    }

    override suspend fun markPendingDeletion(userId: Long) {
        local.setPendingDelete(id = userId, isPendingDelete = true)
    }

    override suspend fun undoPendingDeletion(userId: Long) {
        local.setPendingDelete(id = userId, isPendingDelete = false)
    }

    override suspend fun finaliseDeletion(userId: Long): AppResult<Unit> =
        when (val result = remote.deleteUser(userId)) {
            is AppResult.Success -> {
                local.deleteById(userId)
                AppResult.Success(Unit)
            }

            // The delete did not happen, so the user must not stay hidden: un-hide the row and
            // let the caller surface the error. Silently swallowing it would lose real data
            // from the list on the next launch.
            is AppResult.Failure -> {
                local.setPendingDelete(id = userId, isPendingDelete = false)
                result
            }
        }
}
