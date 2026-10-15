package com.sliide.kmp.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sliide.kmp.domain.error.AppError
import com.sliide.kmp.domain.error.AppResult
import com.sliide.kmp.domain.model.Gender
import com.sliide.kmp.domain.model.UserStatus
import com.sliide.kmp.domain.usecase.CreateUserUseCase
import com.sliide.kmp.domain.validation.UserInputValidator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * Drives the add-user form. Validation runs on every keystroke but errors are only surfaced
 * once a field has been touched, so the form never greets the user in red.
 */
class AddUserViewModel(
    private val createUser: CreateUserUseCase,
    private val validator: UserInputValidator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddUserUiState())
    val uiState: StateFlow<AddUserUiState> = _uiState.asStateFlow()

    private val _events =
        MutableSharedFlow<AddUserEvent>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: Flow<AddUserEvent> = _events.asSharedFlow()

    fun onNameChanged(value: String) {
        _uiState.update {
            it.copy(
                name = value,
                nameError = validator.validateName(value).errorMessage,
                submitError = null,
            )
        }
    }

    fun onEmailChanged(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = validator.validateEmail(value).errorMessage,
                submitError = null,
            )
        }
    }

    fun onNameFocusLost() = _uiState.update { it.copy(isNameTouched = true) }

    fun onEmailFocusLost() = _uiState.update { it.copy(isEmailTouched = true) }

    fun onGenderChanged(gender: Gender) = _uiState.update { it.copy(gender = gender) }

    fun onStatusChanged(status: UserStatus) = _uiState.update { it.copy(status = status) }

    /** Returns the form to its initial state, so the next open starts blank. */
    fun reset() {
        _uiState.value = AddUserUiState()
    }

    fun submit() {
        if (_uiState.value.isSubmitting) {
            return
        }

        // Touch everything so a submit from an untouched form still shows what is wrong.
        _uiState.update {
            it.copy(
                isNameTouched = true,
                isEmailTouched = true,
                nameError = validator.validateName(it.name).errorMessage,
                emailError = validator.validateEmail(it.email).errorMessage,
            )
        }
        if (!_uiState.value.isFormValid) {
            return
        }

        // Claim the in-flight slot synchronously, before the coroutine is launched. Setting
        // it inside the launch leaves a window in which a second tap passes the guard above
        // and creates the user twice - which only looks safe on Android, where
        // Dispatchers.Main.immediate happens to run the body eagerly.
        val submitted = _uiState.updateAndGet { it.copy(isSubmitting = true, submitError = null) }

        viewModelScope.launch {
            val result =
                createUser(
                    name = submitted.name,
                    email = submitted.email,
                    gender = submitted.gender,
                    status = submitted.status,
                )
            when (result) {
                is AppResult.Success -> {
                    // Blanked before the event fires: this ViewModel is scoped to the host,
                    // not to the sheet, so without an explicit clear the next open would
                    // still be showing the user that was just created.
                    val createdName = result.value.name
                    reset()
                    _events.emit(AddUserEvent.Created(createdName))
                }

                is AppResult.Failure ->
                    _uiState.update { state ->
                        val fieldErrors = (result.error as? AppError.Validation)?.fieldErrors.orEmpty()
                        state.copy(
                            isSubmitting = false,
                            // Server-side field errors (422) land on the fields they belong to.
                            nameError = fieldErrors["name"] ?: state.nameError,
                            emailError = fieldErrors["email"] ?: state.emailError,
                            submitError = result.error.takeIf { fieldErrors.isEmpty() },
                        )
                    }
            }
        }
    }
}

data class AddUserUiState(
    val name: String = "",
    val email: String = "",
    val gender: Gender = Gender.Male,
    val status: UserStatus = UserStatus.Active,
    val nameError: String? = null,
    val emailError: String? = null,
    val isNameTouched: Boolean = false,
    val isEmailTouched: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: AppError? = null,
) {
    /** Errors are withheld until the field has been touched, to avoid a red empty form. */
    val visibleNameError: String? get() = nameError.takeIf { isNameTouched }
    val visibleEmailError: String? get() = emailError.takeIf { isEmailTouched }

    val isFormValid: Boolean get() =
        nameError == null &&
            emailError == null &&
            name.isNotBlank() &&
            email.isNotBlank()

    val canSubmit: Boolean get() = isFormValid && !isSubmitting
}

sealed interface AddUserEvent {
    data class Created(
        val name: String,
    ) : AddUserEvent
}
