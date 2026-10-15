package com.sliide.kmp.data

import com.sliide.kmp.data.remote.UserRemoteDataSource
import com.sliide.kmp.data.remote.createHttpClient
import com.sliide.kmp.data.remote.dto.CreateUserRequest
import com.sliide.kmp.domain.error.AppError
import com.sliide.kmp.domain.error.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserRemoteDataSourceTest {
    private lateinit var engine: MockEngine

    /** Requests the engine actually saw - the assertion target for pagination and auth. */
    private val sentRequests get() = engine.requestHistory

    private fun dataSource(
        hasToken: Boolean = true,
        handler: MockRequestHandler,
    ): UserRemoteDataSource {
        engine = MockEngine(handler)
        return UserRemoteDataSource(
            client = createHttpClient(engine = engine, token = if (hasToken) "test-token" else ""),
            baseUrl = "https://example.test/v2",
            hasToken = hasToken,
        )
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun usersJson(vararg ids: Long) =
        ids.joinToString(prefix = "[", postfix = "]") { id ->
            """{"id":$id,"name":"User $id","email":"user$id@example.com","gender":"male","status":"active"}"""
        }

    @Test
    fun `follows the pagination header to fetch the last page`() =
        runTest {
            val source =
                dataSource { request ->
                    val page = request.url.parameters["page"]
                    respond(
                        content = if (page == "7") usersJson(70, 71) else usersJson(1, 2),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                "x-pagination-pages" to listOf("7"),
                            ),
                    )
                }

            val result = source.fetchLastPageOfUsers()

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(70L, 71L), result.value.map { it.id })
            assertEquals(listOf("1", "7"), sentRequests.map { it.url.parameters["page"] })
        }

    @Test
    fun `does not make a second request when there is only one page`() =
        runTest {
            val source =
                dataSource {
                    respond(
                        content = usersJson(1, 2),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                "x-pagination-pages" to listOf("1"),
                            ),
                    )
                }

            val result = source.fetchLastPageOfUsers()

            assertTrue(result is AppResult.Success)
            assertEquals(1, sentRequests.size)
        }

    @Test
    fun `treats a missing pagination header as a single page`() =
        runTest {
            val source =
                dataSource {
                    respond(usersJson(1), HttpStatusCode.OK, jsonHeaders)
                }

            val result = source.fetchLastPageOfUsers()

            assertTrue(result is AppResult.Success)
            assertEquals(1, sentRequests.size)
        }

    @Test
    fun `maps 401 to a token error rather than a generic HTTP failure`() =
        runTest {
            val source = dataSource { respondError(HttpStatusCode.Unauthorized) }

            val result = source.fetchLastPageOfUsers()

            assertEquals(AppError.MissingOrInvalidToken, (result as AppResult.Failure).error)
        }

    @Test
    fun `maps an unexpected status to an HTTP error carrying the code`() =
        runTest {
            val source = dataSource { respondError(HttpStatusCode.InternalServerError) }

            val result = source.fetchLastPageOfUsers()

            val error = (result as AppResult.Failure).error
            assertTrue(error is AppError.Http)
            assertEquals(500, error.code)
        }

    @Test
    fun `maps a dropped connection to the offline error`() =
        runTest {
            val source = dataSource { throw IOException("connection reset") }

            val result = source.fetchLastPageOfUsers()

            assertEquals(AppError.NoInternet, (result as AppResult.Failure).error)
        }

    @Test
    fun `maps a request timeout to the timeout error not to offline`() =
        runTest {
            val source = dataSource { throw HttpRequestTimeoutException("https://example.test", 1_000L) }

            val result = source.fetchLastPageOfUsers()

            // Distinct from NoInternet: the connection worked, the server was just slow.
            assertEquals(AppError.Timeout, (result as AppResult.Failure).error)
        }

    @Test
    fun `an empty last page is a success not a failure`() =
        runTest {
            val source = dataSource { respond("[]", HttpStatusCode.OK, jsonHeaders) }

            val result = source.fetchLastPageOfUsers()

            assertTrue(result is AppResult.Success)
            assertTrue(result.value.isEmpty())
        }

    @Test
    fun `maps a malformed payload to a serialization error`() =
        runTest {
            val source = dataSource { respond("""{"not":"a list"}""", HttpStatusCode.OK, jsonHeaders) }

            val result = source.fetchLastPageOfUsers()

            assertEquals(AppError.Serialization, (result as AppResult.Failure).error)
        }

    @Test
    fun `a 201 create returns the user the server assigned`() =
        runTest {
            val source =
                dataSource {
                    respond(
                        """{"id":99,"name":"Ada","email":"ada@example.com","gender":"female","status":"active"}""",
                        HttpStatusCode.Created,
                        jsonHeaders,
                    )
                }

            val result =
                source.createUser(
                    CreateUserRequest("Ada", "ada@example.com", "female", "active"),
                )

            assertTrue(result is AppResult.Success)
            assertEquals(99L, result.value.id)
        }

    @Test
    fun `a 422 create is unpacked into per-field messages`() =
        runTest {
            val source =
                dataSource {
                    respond(
                        """[{"field":"email","message":"has already been taken"}]""",
                        HttpStatusCode.UnprocessableEntity,
                        jsonHeaders,
                    )
                }

            val result =
                source.createUser(
                    CreateUserRequest("Ada", "ada@example.com", "female", "active"),
                )

            val error = (result as AppResult.Failure).error
            assertTrue(error is AppError.Validation)
            assertEquals(mapOf("email" to "has already been taken"), error.fieldErrors)
        }

    @Test
    fun `a 200 create is rejected because GoRest promises 201`() =
        runTest {
            val source =
                dataSource {
                    respond(
                        """{"id":99,"name":"Ada","email":"ada@example.com","gender":"female","status":"active"}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }

            val result =
                source.createUser(
                    CreateUserRequest("Ada", "ada@example.com", "female", "active"),
                )

            assertTrue(result is AppResult.Failure)
        }

    @Test
    fun `a 204 delete succeeds`() =
        runTest {
            val source = dataSource { respond("", HttpStatusCode.NoContent) }

            assertTrue(source.deleteUser(1) is AppResult.Success)
        }

    @Test
    fun `a 404 delete is treated as success because the row is already gone`() =
        runTest {
            val source = dataSource { respondError(HttpStatusCode.NotFound) }

            assertTrue(source.deleteUser(1) is AppResult.Success)
        }

    @Test
    fun `write calls fail fast when no token is configured`() =
        runTest {
            val source = dataSource(hasToken = false) { respond("", HttpStatusCode.NoContent) }

            assertEquals(AppError.MissingOrInvalidToken, (source.deleteUser(1) as AppResult.Failure).error)
            assertEquals(
                AppError.MissingOrInvalidToken,
                (
                    source.createUser(CreateUserRequest("Ada", "ada@example.com", "female", "active"))
                        as AppResult.Failure
                ).error,
            )
            assertTrue(sentRequests.isEmpty(), "no request should leave the device without a token")
        }

    @Test
    fun `the token is sent as a bearer credential`() =
        runTest {
            val source =
                dataSource {
                    respond(usersJson(1), HttpStatusCode.OK, jsonHeaders)
                }

            source.fetchLastPageOfUsers()

            assertEquals(
                "Bearer test-token",
                sentRequests.single().headers[HttpHeaders.Authorization],
            )
        }
}
