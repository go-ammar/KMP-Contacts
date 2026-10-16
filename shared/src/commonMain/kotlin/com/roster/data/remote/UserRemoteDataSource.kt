package com.roster.data.remote

import com.roster.data.remote.dto.CreateUserRequest
import com.roster.data.remote.dto.FieldErrorDto
import com.roster.data.remote.dto.UserDto
import com.roster.domain.error.AppError
import com.roster.domain.error.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * The GoRest edge. Every method returns [AppResult] rather than throwing, so the repository
 * above it never needs a try/catch and the error taxonomy is decided in exactly one place.
 */
class UserRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val hasToken: Boolean = ApiConfig.hasToken,
) {
    /**
     * The brief asks for the *last* page. GoRest reports the page count in the
     * `x-pagination-pages` response header, so this reads page 1 to learn the total and then
     * fetches the final page - falling back to the page already in hand when there is only one.
     */
    suspend fun fetchLastPageOfUsers(perPage: Int = DEFAULT_PAGE_SIZE): AppResult<List<UserDto>> =
        safeCall {
            val firstPage =
                client.get("$baseUrl/users") {
                    parameter("page", 1)
                    parameter("per_page", perPage)
                }
            firstPage.failureOrNull()?.let { throw ApiFailure(it) }

            val totalPages = firstPage.headers[HEADER_PAGINATION_PAGES]?.toIntOrNull() ?: 1
            if (totalPages <= 1) {
                return@safeCall firstPage.body<List<UserDto>>()
            }

            val lastPage =
                client.get("$baseUrl/users") {
                    parameter("page", totalPages)
                    parameter("per_page", perPage)
                }
            lastPage.failureOrNull()?.let { throw ApiFailure(it) }
            lastPage.body<List<UserDto>>()
        }

    suspend fun createUser(request: CreateUserRequest): AppResult<UserDto> {
        if (!hasToken) {
            return AppResult.Failure(AppError.MissingOrInvalidToken)
        }
        return safeCall {
            val response =
                client.post("$baseUrl/users") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            if (response.status != HttpStatusCode.Created) {
                throw ApiFailure(response.toAppError(parseFieldErrors = true))
            }
            response.body<UserDto>()
        }
    }

    suspend fun deleteUser(id: Long): AppResult<Unit> {
        if (!hasToken) {
            return AppResult.Failure(AppError.MissingOrInvalidToken)
        }
        return safeCall {
            val response = client.delete("$baseUrl/users/$id")
            // A 404 means the row is already gone server-side, which satisfies the caller's intent.
            if (response.status != HttpStatusCode.NoContent && response.status != HttpStatusCode.NotFound) {
                throw ApiFailure(response.toAppError(parseFieldErrors = false))
            }
        }
    }

    private suspend fun HttpResponse.failureOrNull(): AppError? =
        if (status.isSuccess()) null else toAppError(parseFieldErrors = false)

    private suspend fun HttpResponse.toAppError(parseFieldErrors: Boolean): AppError =
        when {
            status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ->
                AppError.MissingOrInvalidToken

            status == HttpStatusCode.UnprocessableEntity && parseFieldErrors ->
                AppError.Validation(readFieldErrors())

            else -> AppError.Http(status.value, status.description)
        }

    private suspend fun HttpResponse.readFieldErrors(): Map<String, String> =
        runCatching {
            body<List<FieldErrorDto>>()
                .mapNotNull { dto -> dto.field?.let { it to (dto.message ?: "is invalid") } }
                .toMap()
        }.getOrElse { emptyMap() }

    /** Carries an already-classified [AppError] out of the lambda passed to [safeCall]. */
    private class ApiFailure(
        val error: AppError,
    ) : Exception(error.toString())

    private inline fun <T> safeCall(block: () -> T): AppResult<T> =
        try {
            AppResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: ApiFailure) {
            AppResult.Failure(failure.error)
        } catch (timeout: HttpRequestTimeoutException) {
            AppResult.Failure(AppError.Timeout)
        } catch (timeout: ConnectTimeoutException) {
            AppResult.Failure(AppError.Timeout)
        } catch (timeout: SocketTimeoutException) {
            AppResult.Failure(AppError.Timeout)
        } catch (serialization: SerializationException) {
            AppResult.Failure(AppError.Serialization)
        } catch (conversion: ContentConvertException) {
            // Ktor wraps a body that will not deserialize in its own exception type, which does
            // not extend SerializationException - without this it would fall through to Unknown.
            AppResult.Failure(AppError.Serialization)
        } catch (io: IOException) {
            AppResult.Failure(AppError.NoInternet)
        } catch (other: Throwable) {
            AppResult.Failure(other.toTransportError())
        }

    /**
     * DNS failures surface as `UnresolvedAddressException`, which is neither an `IOException`
     * nor a common Ktor type, so it is matched by name rather than by class.
     */
    private fun Throwable.toTransportError(): AppError =
        if (this::class.simpleName == "UnresolvedAddressException") {
            AppError.NoInternet
        } else {
            AppError.Unknown(message)
        }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val HEADER_PAGINATION_PAGES = "x-pagination-pages"
    }
}
