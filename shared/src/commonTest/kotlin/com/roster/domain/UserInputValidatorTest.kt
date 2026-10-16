package com.roster.domain

import com.roster.domain.validation.UserInputValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserInputValidatorTest {
    private val validator = UserInputValidator()

    @Test
    fun `accepts ordinary names`() {
        listOf("Ada Lovelace", "Jean-Luc Picard", "O'Neill", "Ana Sofía Pérez", "李雷")
            .forEach { assertTrue(validator.validateName(it).isValid, "expected '$it' to be valid") }
    }

    @Test
    fun `rejects empty and whitespace-only names`() {
        assertEquals("Name is required", validator.validateName("").errorMessage)
        assertEquals("Name is required", validator.validateName("   ").errorMessage)
    }

    @Test
    fun `rejects names that are too short or too long`() {
        assertNotNull(validator.validateName("A").errorMessage)
        assertNotNull(validator.validateName("A".repeat(61)).errorMessage)
        assertTrue(validator.validateName("A".repeat(60)).isValid)
    }

    @Test
    fun `rejects digits and symbols in names`() {
        listOf("User123", "Bobby <script>", "a@b", "Name_With_Underscore")
            .forEach { assertFalse(validator.validateName(it).isValid, "expected '$it' to be rejected") }
    }

    @Test
    fun `trims before validating so padded input is accepted`() {
        assertTrue(validator.validateName("  Ada Lovelace  ").isValid)
        assertTrue(validator.validateEmail("  ada@example.com  ").isValid)
    }

    @Test
    fun `accepts realistic email addresses`() {
        listOf(
            "ada@example.com",
            "ada.lovelace@example.co.uk",
            "ada+newsletter@example.io",
            "a@b.co",
            "first_last@sub.domain.example.org",
        ).forEach { assertTrue(validator.validateEmail(it).isValid, "expected '$it' to be valid") }
    }

    @Test
    fun `rejects malformed email addresses`() {
        listOf(
            "",
            "ada",
            "ada@",
            "@example.com",
            "ada@@example.com",
            "ada@example",
            "ada@.com",
            "ada@example..com",
            "ada @example.com",
            "ada@exa mple.com",
            "ada@-example.com",
            "ada@example-.com",
            ".ada@example.com",
            "ada.@example.com",
            "ada@example.c",
        ).forEach { assertFalse(validator.validateEmail(it).isValid, "expected '$it' to be rejected") }
    }

    @Test
    fun `rejects an email beyond the practical length limit`() {
        val tooLong = "a".repeat(250) + "@example.com"
        assertNotNull(validator.validateEmail(tooLong).errorMessage)
    }

    @Test
    fun `reports a single at-sign requirement before shape`() {
        assertEquals("Email must contain a single @", validator.validateEmail("ada@@example.com").errorMessage)
        assertEquals("Email must contain a single @", validator.validateEmail("adaexample.com").errorMessage)
    }
}
