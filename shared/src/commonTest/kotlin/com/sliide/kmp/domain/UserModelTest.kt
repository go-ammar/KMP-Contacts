package com.sliide.kmp.domain

import com.sliide.kmp.domain.model.Gender
import com.sliide.kmp.domain.model.UserStatus
import com.sliide.kmp.fake.user
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `initials` and the `fromWire` fallbacks run on every row of every response, but were only
 * ever exercised indirectly through a single well-formed name. GoRest returns titles,
 * single-word names and null genders, so these are production paths rather than theory.
 */
class UserModelTest {
    @Test
    fun `initials take the first letter of the first two words`() {
        assertEquals("AL", user(1, name = "Ada Lovelace").initials)
        assertEquals("SJ", user(1, name = "Sen. Jay Somayaji").initials)
    }

    @Test
    fun `a single-word name yields one initial`() {
        assertEquals("C", user(1, name = "Cher").initials)
    }

    @Test
    fun `extra whitespace does not produce blank initials`() {
        assertEquals("AL", user(1, name = "  Ada   Lovelace  ").initials)
    }

    @Test
    fun `a name with no letters falls back to a placeholder`() {
        // The `?` branch is what stops the avatar rendering as an empty circle.
        assertEquals("?", user(1, name = "").initials)
        assertEquals("?", user(1, name = "    ").initials)
    }

    @Test
    fun `initials are upper-cased regardless of input casing`() {
        assertEquals("AL", user(1, name = "ada lovelace").initials)
    }

    @Test
    fun `initials work for non-latin scripts`() {
        assertEquals("李", user(1, name = "李雷").initials)
    }

    @Test
    fun `gender falls back to male when absent or unrecognised`() {
        // UserDto.gender is nullable, so this runs whenever GoRest omits the field.
        assertEquals(Gender.Male, Gender.fromWire(null))
        assertEquals(Gender.Male, Gender.fromWire("non-binary"))
        assertEquals(Gender.Male, Gender.fromWire(""))
    }

    @Test
    fun `gender parsing ignores case`() {
        assertEquals(Gender.Female, Gender.fromWire("FEMALE"))
        assertEquals(Gender.Female, Gender.fromWire("Female"))
    }

    @Test
    fun `status falls back to active when absent or unrecognised`() {
        assertEquals(UserStatus.Active, UserStatus.fromWire(null))
        assertEquals(UserStatus.Active, UserStatus.fromWire("pending"))
    }

    @Test
    fun `status parsing ignores case`() {
        assertEquals(UserStatus.Inactive, UserStatus.fromWire("INACTIVE"))
    }
}
