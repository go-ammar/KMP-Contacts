package com.sliide.kmp.domain.validation

/**
 * Field-level validation for the add-user form. Pure and synchronous so the UI can call it
 * on every keystroke, and so it can be exhaustively unit tested without a dispatcher.
 */
class UserInputValidator {
    fun validateName(raw: String): ValidationResult {
        val name = raw.trim()
        return when {
            name.isEmpty() -> ValidationResult.Invalid("Name is required")
            name.length < MIN_NAME_LENGTH ->
                ValidationResult.Invalid(
                    "Name must be at least $MIN_NAME_LENGTH characters",
                )
            name.length > MAX_NAME_LENGTH ->
                ValidationResult.Invalid(
                    "Name must be $MAX_NAME_LENGTH characters or fewer",
                )
            !NAME_REGEX.matches(
                name,
            ) -> ValidationResult.Invalid("Name can only contain letters, spaces, hyphens and apostrophes")
            else -> ValidationResult.Valid
        }
    }

    fun validateEmail(raw: String): ValidationResult {
        val email = raw.trim()
        return when {
            email.isEmpty() -> ValidationResult.Invalid("Email is required")
            email.length > MAX_EMAIL_LENGTH ->
                ValidationResult.Invalid(
                    "Email must be $MAX_EMAIL_LENGTH characters or fewer",
                )
            email.count { it == '@' } != 1 -> ValidationResult.Invalid("Email must contain a single @")
            !EMAIL_REGEX.matches(email) -> ValidationResult.Invalid("Enter a valid email address")
            else -> ValidationResult.Valid
        }
    }

    private companion object {
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 60
        const val MAX_EMAIL_LENGTH = 254

        /** Letters (any script), spaces, hyphens, apostrophes and full stops. */
        val NAME_REGEX = Regex("""^[\p{L}][\p{L} .'\-]*[\p{L}.]$""")

        /**
         * Pragmatic address check: a dot-separated local part of unreserved characters, a
         * domain of hyphen-free-edged labels, and a TLD of at least two letters. Deliberately
         * stricter than RFC 5322 - it rejects the exotic-but-legal forms nobody types into a
         * sign-up form, and accepts everything a real user will.
         */
        val EMAIL_REGEX =
            Regex(
                """^[A-Za-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*""" +
                    """@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}${'$'}""",
            )
    }
}

sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Invalid(
        val message: String,
    ) : ValidationResult

    val errorMessage: String?
        get() = (this as? Invalid)?.message

    val isValid: Boolean
        get() = this is Valid
}
