package com.sliide.kmp.presentation

import app.cash.turbine.test
import com.sliide.kmp.domain.error.AppError
import com.sliide.kmp.domain.error.AppResult
import com.sliide.kmp.domain.model.Gender
import com.sliide.kmp.domain.model.UserStatus
import com.sliide.kmp.domain.usecase.CreateUserUseCase
import com.sliide.kmp.domain.validation.UserInputValidator
import com.sliide.kmp.fake.FakeUserRepository
import com.sliide.kmp.presentation.users.AddUserEvent
import com.sliide.kmp.presentation.users.AddUserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AddUserViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val validator = UserInputValidator()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: FakeUserRepository) =
        AddUserViewModel(
            createUser = CreateUserUseCase(repository, validator),
            validator = validator,
        )

    @Test
    fun `an untouched form shows no errors even though it is invalid`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeUserRepository())

            val state = viewModel.uiState.value
            assertNull(state.visibleNameError)
            assertNull(state.visibleEmailError)
            assertFalse(state.canSubmit)
        }

    @Test
    fun `errors appear only after the field has been left`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeUserRepository())

            viewModel.onEmailChanged("not-an-email")
            assertNull(viewModel.uiState.value.visibleEmailError)

            viewModel.onEmailFocusLost()
            assertNotNull(viewModel.uiState.value.visibleEmailError)
        }

    @Test
    fun `submitting an untouched form reveals every error at once`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository()
            val viewModel = viewModel(repository)

            viewModel.submit()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.visibleNameError)
            assertNotNull(state.visibleEmailError)
        }

    @Test
    fun `submit is blocked while either field is invalid`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeUserRepository())

            viewModel.onNameChanged("Ada Lovelace")
            viewModel.onEmailChanged("ada@")
            assertFalse(viewModel.uiState.value.canSubmit)

            viewModel.onEmailChanged("ada@example.com")
            assertTrue(viewModel.uiState.value.canSubmit)
        }

    @Test
    fun `a successful submit announces the created user`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository()
            val viewModel = viewModel(repository)

            viewModel.onNameChanged("Ada Lovelace")
            viewModel.onEmailChanged("Ada@Example.COM")

            viewModel.events.test {
                viewModel.submit()
                advanceUntilIdle()

                val event = awaitItem()
                assertTrue(event is AddUserEvent.Created)
                assertEquals("Ada Lovelace", event.name)
            }
            assertFalse(viewModel.uiState.value.isSubmitting)
        }

    @Test
    fun `the email is normalised to lower case before it is sent`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository()
            val viewModel = viewModel(repository)

            viewModel.onNameChanged("  Ada Lovelace  ")
            viewModel.onEmailChanged("Ada@Example.COM")
            viewModel.submit()
            advanceUntilIdle()

            val created = repository.currentUsers().single()
            assertEquals("ada@example.com", created.email)
            assertEquals("Ada Lovelace", created.name)
        }

    @Test
    fun `server-side field errors land on the fields they belong to`() =
        runTest(dispatcher) {
            val repository =
                FakeUserRepository().apply {
                    createResult =
                        AppResult.Failure(
                            AppError.Validation(mapOf("email" to "has already been taken")),
                        )
                }
            val viewModel = viewModel(repository)

            viewModel.onNameChanged("Ada Lovelace")
            viewModel.onEmailChanged("ada@example.com")
            viewModel.submit()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("has already been taken", state.visibleEmailError)
            assertNull(state.submitError, "a field-level error should not also appear as a form error")
        }

    @Test
    fun `a transport failure surfaces as a form-level error`() =
        runTest(dispatcher) {
            val repository =
                FakeUserRepository().apply {
                    createResult = AppResult.Failure(AppError.NoInternet)
                }
            val viewModel = viewModel(repository)

            viewModel.onNameChanged("Ada Lovelace")
            viewModel.onEmailChanged("ada@example.com")
            viewModel.submit()
            advanceUntilIdle()

            assertEquals(AppError.NoInternet, viewModel.uiState.value.submitError)
            assertFalse(viewModel.uiState.value.isSubmitting)
        }

    @Test
    fun `a missing token is explained rather than reported as a generic failure`() =
        runTest(dispatcher) {
            val repository =
                FakeUserRepository().apply {
                    createResult = AppResult.Failure(AppError.MissingOrInvalidToken)
                }
            val viewModel = viewModel(repository)

            viewModel.onNameChanged("Ada Lovelace")
            viewModel.onEmailChanged("ada@example.com")
            viewModel.submit()
            advanceUntilIdle()

            assertEquals(AppError.MissingOrInvalidToken, viewModel.uiState.value.submitError)
        }

    @Test
    fun `the form is blank again after a successful create`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository()
            val viewModel = viewModel(repository)

            viewModel.onNameChanged("Ada Lovelace")
            viewModel.onEmailChanged("ada@example.com")
            viewModel.onGenderChanged(Gender.Female)
            viewModel.onStatusChanged(UserStatus.Inactive)
            viewModel.submit()
            advanceUntilIdle()

            // This ViewModel outlives the sheet that hosts it, so a stale form would reappear
            // pre-filled with the user that was just created.
            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertEquals("", state.email)
            assertEquals(Gender.Male, state.gender)
            assertEquals(UserStatus.Active, state.status)
            assertFalse(state.isNameTouched)
            assertFalse(state.isEmailTouched)
            assertNull(state.submitError)
            assertFalse(state.canSubmit)
        }

    @Test
    fun `reset clears a part-filled form and its errors`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeUserRepository())

            viewModel.onNameChanged("A")
            viewModel.onEmailChanged("nope")
            viewModel.onNameFocusLost()
            viewModel.onEmailFocusLost()
            assertNotNull(viewModel.uiState.value.visibleEmailError)

            viewModel.reset()

            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertEquals("", state.email)
            assertNull(state.visibleNameError)
            assertNull(state.visibleEmailError)
        }

    @Test
    fun `a failed create keeps what the user typed so they can fix it`() =
        runTest(dispatcher) {
            val repository =
                FakeUserRepository().apply {
                    createResult = AppResult.Failure(AppError.NoInternet)
                }
            val viewModel = viewModel(repository)

            viewModel.onNameChanged("Ada Lovelace")
            viewModel.onEmailChanged("ada@example.com")
            viewModel.submit()
            advanceUntilIdle()

            assertEquals("Ada Lovelace", viewModel.uiState.value.name)
            assertEquals("ada@example.com", viewModel.uiState.value.email)
        }

    @Test
    fun `a second submit while one is in flight does not create the user twice`() =
        runTest(dispatcher) {
            val repository = FakeUserRepository()
            val viewModel = viewModel(repository)

            viewModel.onNameChanged("Ada Lovelace")
            viewModel.onEmailChanged("ada@example.com")

            // Double-tap on a slow connection is the obvious way to get a duplicate user.
            viewModel.submit()
            viewModel.submit()
            advanceUntilIdle()

            assertEquals(1, repository.currentUsers().size)
        }

    @Test
    fun `gender and status default to a valid pair and can be changed`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeUserRepository())

            assertEquals(Gender.Male, viewModel.uiState.value.gender)
            assertEquals(UserStatus.Active, viewModel.uiState.value.status)

            viewModel.onGenderChanged(Gender.Female)
            viewModel.onStatusChanged(UserStatus.Inactive)

            assertEquals(Gender.Female, viewModel.uiState.value.gender)
            assertEquals(UserStatus.Inactive, viewModel.uiState.value.status)
        }
}
