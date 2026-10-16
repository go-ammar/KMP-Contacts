package com.roster.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.roster.data.local.RosterDatabase
import com.roster.data.local.UserLocalDataSource
import com.roster.data.remote.UserRemoteDataSource
import com.roster.data.remote.createHttpClient
import com.roster.data.repository.UserRepositoryImpl
import com.roster.domain.error.AppError
import com.roster.domain.error.AppResult
import com.roster.domain.model.Gender
import com.roster.domain.model.NewUser
import com.roster.domain.model.UserStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the repository against a real in-memory SQLite database. The fakes elsewhere cannot
 * catch SQL-level mistakes - notably whether a refresh silently resets a cached user's age.
 */
class UserRepositoryIntegrationTest {
    private val driver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            .also { RosterDatabase.Schema.create(it) }
    private val database = RosterDatabase(driver)
    private val local = UserLocalDataSource(database)

    private var currentTime = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val clock =
        object : Clock {
            override fun now(): Instant = currentTime
        }

    @AfterTest
    fun tearDown() = driver.close()

    private fun repository(handler: MockRequestHandler) =
        UserRepositoryImpl(
            remote =
                UserRemoteDataSource(
                    client = createHttpClient(engine = MockEngine(handler), token = "test-token"),
                    baseUrl = "https://example.test/v2",
                    hasToken = true,
                ),
            local = local,
            clock = clock,
        )

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun usersJson(vararg ids: Long) =
        ids.joinToString(prefix = "[", postfix = "]") { id ->
            """{"id":$id,"name":"User $id","email":"user$id@example.com","gender":"male","status":"active"}"""
        }

    @Test
    fun `a refresh caches the page it fetched`() =
        runTest {
            val repository = repository { respond(usersJson(1, 2), HttpStatusCode.OK, jsonHeaders) }

            assertTrue(repository.refresh() is AppResult.Success)

            assertEquals(listOf(2L, 1L), repository.observeUsers().first().map { it.id })
            assertTrue(repository.hasCachedData())
        }

    @Test
    fun `a second refresh does not reset how old a cached user is`() =
        runTest {
            val repository = repository { respond(usersJson(1), HttpStatusCode.OK, jsonHeaders) }

            repository.refresh()
            val firstSeen =
                repository
                    .observeUsers()
                    .first()
                    .single()
                    .firstSeenAt

            currentTime = currentTime.plus(kotlin.time.Duration.parse("2h"))
            repository.refresh()

            assertEquals(
                firstSeen,
                repository
                    .observeUsers()
                    .first()
                    .single()
                    .firstSeenAt,
            )
        }

    @Test
    fun `a refresh still picks up changed fields on an existing user`() =
        runTest {
            var payload = """[{"id":1,"name":"Ada","email":"ada@example.com","gender":"female","status":"active"}]"""
            val repository = repository { respond(payload, HttpStatusCode.OK, jsonHeaders) }

            repository.refresh()
            payload = """[{"id":1,"name":"Ada Lovelace","email":"ada@new.com","gender":"female","status":"inactive"}]"""
            repository.refresh()

            val user = repository.observeUsers().first().single()
            assertEquals("Ada Lovelace", user.name)
            assertEquals("ada@new.com", user.email)
            assertEquals(UserStatus.Inactive, user.status)
        }

    @Test
    fun `a created user is cached at the top of the list straight away`() =
        runTest {
            val repository =
                repository { request ->
                    if (request.method.value == "POST") {
                        respond(
                            """{"id":99,"name":"Ada","email":"ada@example.com","gender":"female","status":"active"}""",
                            HttpStatusCode.Created,
                            jsonHeaders,
                        )
                    } else {
                        respond(usersJson(1, 2), HttpStatusCode.OK, jsonHeaders)
                    }
                }

            repository.refresh()
            currentTime = currentTime.plus(kotlin.time.Duration.parse("1h"))
            val created =
                repository.createUser(
                    NewUser("Ada", "ada@example.com", Gender.Female, UserStatus.Active),
                )

            assertTrue(created is AppResult.Success)
            assertEquals(
                99L,
                repository
                    .observeUsers()
                    .first()
                    .first()
                    .id,
            )
        }

    @Test
    fun `a pending deletion hides the user without removing the row`() =
        runTest {
            val repository = repository { respond(usersJson(1, 2), HttpStatusCode.OK, jsonHeaders) }
            repository.refresh()

            repository.markPendingDeletion(1)

            assertEquals(listOf(2L), repository.observeUsers().first().map { it.id })
            assertTrue(repository.hasCachedData())
        }

    @Test
    fun `undoing a pending deletion brings the user back`() =
        runTest {
            val repository = repository { respond(usersJson(1, 2), HttpStatusCode.OK, jsonHeaders) }
            repository.refresh()

            repository.markPendingDeletion(1)
            repository.undoPendingDeletion(1)

            assertEquals(listOf(2L, 1L), repository.observeUsers().first().map { it.id })
        }

    @Test
    fun `a refresh never resurrects a user inside their undo window`() =
        runTest {
            val repository = repository { respond(usersJson(1, 2), HttpStatusCode.OK, jsonHeaders) }
            repository.refresh()
            repository.markPendingDeletion(1)

            repository.refresh()

            assertEquals(listOf(2L), repository.observeUsers().first().map { it.id })
        }

    @Test
    fun `finalising a deletion drops the row for good`() =
        runTest {
            val repository =
                repository { request ->
                    if (request.method.value == "DELETE") {
                        respond("", HttpStatusCode.NoContent)
                    } else {
                        respond(usersJson(1, 2), HttpStatusCode.OK, jsonHeaders)
                    }
                }
            repository.refresh()
            repository.markPendingDeletion(1)

            assertTrue(repository.finaliseDeletion(1) is AppResult.Success)

            assertEquals(listOf(2L), repository.observeUsers().first().map { it.id })
        }

    @Test
    fun `a failed delete puts the user back rather than losing them`() =
        runTest {
            val repository =
                repository { request ->
                    if (request.method.value == "DELETE") {
                        respondError(HttpStatusCode.InternalServerError)
                    } else {
                        respond(usersJson(1, 2), HttpStatusCode.OK, jsonHeaders)
                    }
                }
            repository.refresh()
            repository.markPendingDeletion(1)

            val result = repository.finaliseDeletion(1)

            assertTrue(result is AppResult.Failure)
            assertEquals(listOf(2L, 1L), repository.observeUsers().first().map { it.id })
        }

    @Test
    fun `deletions abandoned by a killed process are restored on the next start`() =
        runTest {
            val repository = repository { respond(usersJson(1, 2), HttpStatusCode.OK, jsonHeaders) }
            repository.refresh()
            repository.markPendingDeletion(1)

            repository.restoreAbandonedDeletions()

            assertEquals(listOf(2L, 1L), repository.observeUsers().first().map { it.id })
        }

    @Test
    fun `a failed refresh leaves the cache intact for offline use`() =
        runTest {
            repository { respond(usersJson(1, 2), HttpStatusCode.OK, jsonHeaders) }.refresh()

            val offline = repository { respondError(HttpStatusCode.ServiceUnavailable) }
            val result = offline.refresh()

            assertTrue(result is AppResult.Failure)
            assertEquals(listOf(2L, 1L), offline.observeUsers().first().map { it.id })
        }

    @Test
    fun `writes are refused before they leave the device when no token is configured`() =
        runTest {
            val tokenless =
                UserRepositoryImpl(
                    remote =
                        UserRemoteDataSource(
                            client =
                                createHttpClient(
                                    engine = MockEngine { respond("", HttpStatusCode.NoContent) },
                                    token = "",
                                ),
                            baseUrl = "https://example.test/v2",
                            hasToken = false,
                        ),
                    local = local,
                    clock = clock,
                )

            val result = tokenless.createUser(NewUser("Ada", "ada@example.com", Gender.Female, UserStatus.Active))

            assertEquals(AppError.MissingOrInvalidToken, (result as AppResult.Failure).error)
        }
}
