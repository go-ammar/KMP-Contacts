package com.sliide.kmp.domain.usecase

import com.sliide.kmp.domain.error.AppError
import com.sliide.kmp.domain.error.AppResult
import com.sliide.kmp.domain.model.Gender
import com.sliide.kmp.domain.model.NewUser
import com.sliide.kmp.domain.model.User
import com.sliide.kmp.domain.model.UserStatus
import com.sliide.kmp.domain.repository.UserRepository
import com.sliide.kmp.domain.validation.UserInputValidator
import kotlinx.coroutines.flow.Flow

/**
 * Thin, named entry points onto [UserRepository]. They exist so the ViewModels depend on
 * one verb each rather than on the whole repository surface, which keeps their tests small.
 */

class ObserveUsersUseCase(
    private val repository: UserRepository,
) {
    operator fun invoke(): Flow<List<User>> = repository.observeUsers()
}

class RefreshUsersUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refresh()
}

class HasCachedUsersUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(): Boolean = repository.hasCachedData()
}

/**
 * Validates before hitting the network so an obviously bad payload never leaves the device.
 * The form validates per keystroke too; this is the backstop for programmatic callers.
 */
class CreateUserUseCase(
    private val repository: UserRepository,
    private val validator: UserInputValidator,
) {
    suspend operator fun invoke(
        name: String,
        email: String,
        gender: Gender,
        status: UserStatus,
    ): AppResult<User> {
        val nameResult = validator.validateName(name)
        val emailResult = validator.validateEmail(email)
        val fieldErrors =
            buildMap {
                nameResult.errorMessage?.let { put("name", it) }
                emailResult.errorMessage?.let { put("email", it) }
            }
        if (fieldErrors.isNotEmpty()) {
            return AppResult.Failure(AppError.Validation(fieldErrors))
        }
        return repository.createUser(
            NewUser(
                name = name.trim(),
                email = email.trim().lowercase(),
                gender = gender,
                status = status,
            ),
        )
    }
}

/**
 * Clears any deletion whose undo window was cut short by the process dying. Runs at start-up:
 * if the DELETE never left the device, the user was never deleted.
 */
class RestoreAbandonedDeletionsUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke() = repository.restoreAbandonedDeletions()
}

class MarkUserPendingDeletionUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(userId: Long) = repository.markPendingDeletion(userId)
}

class UndoUserDeletionUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(userId: Long) = repository.undoPendingDeletion(userId)
}

class FinaliseUserDeletionUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(userId: Long): AppResult<Unit> = repository.finaliseDeletion(userId)
}
