package com.roster.domain.error

/**
 * A deliberately small result type. [kotlin.Result] is avoided because it cannot carry a
 * typed error and does not survive the Kotlin/Native boundary comfortably.
 */
sealed interface AppResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : AppResult<T>

    data class Failure(
        val error: AppError,
    ) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> =
    also {
        if (this is AppResult.Success) {
            action(value)
        }
    }

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> =
    also {
        if (this is AppResult.Failure) {
            action(error)
        }
    }

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error
