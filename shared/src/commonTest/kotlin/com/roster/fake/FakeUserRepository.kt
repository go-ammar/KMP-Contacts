package com.roster.fake

import com.roster.domain.error.AppResult
import com.roster.domain.model.Gender
import com.roster.domain.model.NewUser
import com.roster.domain.model.User
import com.roster.domain.model.UserStatus
import com.roster.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Instant

/**
 * An in-memory stand-in for the real repository. It models the pending-delete flag faithfully
 * so the ViewModel tests exercise the same visibility rules the database enforces.
 */
class FakeUserRepository(
    initialUsers: List<User> = emptyList(),
) : UserRepository {
    private val storedUsers = MutableStateFlow(initialUsers)
    private val pendingDeleteIds = MutableStateFlow<Set<Long>>(emptySet())

    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var createResult: AppResult<User>? = null
    var deleteResult: AppResult<Unit> = AppResult.Success(Unit)

    var refreshCount = 0
        private set
    var restoreAbandonedCount = 0
        private set
    val finalisedIds = mutableListOf<Long>()
    val undoneIds = mutableListOf<Long>()

    /** The currently visible users, for assertions that do not need to collect the flow. */
    fun currentUsers(): List<User> = storedUsers.value.filterNot { it.id in pendingDeleteIds.value }

    /** Lets a test publish a page of users as though a sync had just landed. */
    fun emit(users: List<User>) {
        storedUsers.value = users
    }

    // Combined rather than mapped so a change to either source re-emits, which is what the
    // SQLDelight-backed implementation does.
    override fun observeUsers(): Flow<List<User>> =
        combine(storedUsers, pendingDeleteIds) { users, pending ->
            users.filterNot { it.id in pending }
        }

    override suspend fun hasCachedData(): Boolean = storedUsers.value.isNotEmpty()

    override suspend fun refresh(): AppResult<Unit> {
        refreshCount++
        return refreshResult
    }

    override suspend fun createUser(newUser: NewUser): AppResult<User> {
        val result =
            createResult ?: AppResult.Success(
                User(
                    id = (storedUsers.value.maxOfOrNull { it.id } ?: 0L) + 1,
                    name = newUser.name,
                    email = newUser.email,
                    gender = newUser.gender,
                    status = newUser.status,
                    firstSeenAt = Instant.fromEpochMilliseconds(0),
                ),
            )
        if (result is AppResult.Success) {
            storedUsers.value = listOf(result.value) + storedUsers.value
        }
        return result
    }

    override suspend fun markPendingDeletion(userId: Long) {
        pendingDeleteIds.value = pendingDeleteIds.value + userId
    }

    override suspend fun undoPendingDeletion(userId: Long) {
        undoneIds += userId
        pendingDeleteIds.value = pendingDeleteIds.value - userId
    }

    override suspend fun finaliseDeletion(userId: Long): AppResult<Unit> {
        finalisedIds += userId
        return when (deleteResult) {
            is AppResult.Success -> {
                storedUsers.value = storedUsers.value.filterNot { it.id == userId }
                pendingDeleteIds.value = pendingDeleteIds.value - userId
                AppResult.Success(Unit)
            }

            is AppResult.Failure -> {
                pendingDeleteIds.value = pendingDeleteIds.value - userId
                deleteResult
            }
        }
    }

    override suspend fun restoreAbandonedDeletions() {
        restoreAbandonedCount++
        pendingDeleteIds.value = emptySet()
    }
}

fun user(
    id: Long,
    name: String = "User $id",
    email: String = "user$id@example.com",
    gender: Gender = Gender.Female,
    status: UserStatus = UserStatus.Active,
    firstSeenAtMillis: Long = 0,
): User =
    User(
        id = id,
        name = name,
        email = email,
        gender = gender,
        status = status,
        firstSeenAt = Instant.fromEpochMilliseconds(firstSeenAtMillis),
    )
