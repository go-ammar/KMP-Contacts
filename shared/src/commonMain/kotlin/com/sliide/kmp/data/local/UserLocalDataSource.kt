package com.sliide.kmp.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.sliide.kmp.data.mapper.toDomain
import com.sliide.kmp.domain.model.User
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The cache. Reads are a [Flow] straight off SQLDelight, so any write - from a refresh, a
 * create, or an undo - re-renders the list without the ViewModel re-querying.
 */
class UserLocalDataSource(
    database: SliideDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val queries = database.userEntityQueries

    fun observeVisibleUsers(): Flow<List<User>> =
        queries
            .selectVisible()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun countAll(): Long =
        withContext(ioDispatcher) {
            queries
                .countAll()
                .asFlow()
                .mapToOne(ioDispatcher)
                .first()
        }

    /**
     * Writes the whole page in one transaction. Rows already present keep their
     * `firstSeenAtEpochMillis` and their pending-delete flag - see `UserEntity.sq`.
     */
    suspend fun upsertAll(users: List<User>) =
        withContext(ioDispatcher) {
            queries.transaction {
                users.forEach { user ->
                    queries.insertIfAbsent(
                        id = user.id,
                        name = user.name,
                        email = user.email,
                        gender = user.gender.wireValue,
                        status = user.status.wireValue,
                        firstSeenAtEpochMillis = user.firstSeenAt.toEpochMilliseconds(),
                    )
                    queries.updateMutableFields(
                        name = user.name,
                        email = user.email,
                        gender = user.gender.wireValue,
                        status = user.status.wireValue,
                        id = user.id,
                    )
                }
            }
        }

    suspend fun upsert(user: User) = upsertAll(listOf(user))

    suspend fun setPendingDelete(
        id: Long,
        isPendingDelete: Boolean,
    ) = withContext(ioDispatcher) {
        queries.setPendingDelete(isPendingDelete = if (isPendingDelete) 1L else 0L, id = id)
    }

    suspend fun deleteById(id: Long) =
        withContext(ioDispatcher) {
            queries.deleteById(id)
        }

    suspend fun clearAllPendingDeletes() =
        withContext(ioDispatcher) {
            queries.clearAllPendingDeletes()
        }
}
