package com.sliide.kmp.domain.error

/** Every failure the UI can be asked to render, as a closed set. */
sealed interface AppError {
    /** The device has no usable connection, or the host could not be reached. */
    data object NoInternet : AppError

    /** The request reached the server but took too long to complete. */
    data object Timeout : AppError

    /** No GoRest token is configured, or the server rejected it (401/403). */
    data object MissingOrInvalidToken : AppError

    /** The server rejected the payload (422) with per-field messages. */
    data class Validation(
        val fieldErrors: Map<String, String>,
    ) : AppError

    /** Any other non-success HTTP status. */
    data class Http(
        val code: Int,
        val description: String?,
    ) : AppError

    /** The response body did not match the expected shape. */
    data object Serialization : AppError

    data class Unknown(
        val description: String?,
    ) : AppError
}
