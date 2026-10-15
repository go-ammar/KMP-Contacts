package com.sliide.kmp.ui.users

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import com.sliide.kmp.presentation.users.UserUiModel
import com.sliide.kmp.ui.theme.SliideTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The brief makes long-press the entry point to deleting a user, so the gesture itself is a
 * requirement rather than an implementation detail. It cannot be covered by a JVM unit test -
 * there is no touch input - and synthetic `adb input` does not reproduce it reliably, so it is
 * pinned here instead.
 */
@OptIn(ExperimentalTestApi::class)
class UserRowGestureTest {
    private val user =
        UserUiModel(
            id = 42,
            name = "Ada Lovelace",
            email = "ada@example.com",
            initials = "AL",
            relativeTimestamp = "5 minutes ago",
            isActive = true,
            genderLabel = "Female",
        )

    @Test
    fun longPressingARowRequestsDeletionForThatUser() =
        runComposeUiTest {
            var longClickedId: Long? = null
            var clickedId: Long? = null

            setContent {
                SliideTheme {
                    UserRow(
                        user = user,
                        isSelected = false,
                        onClick = { clickedId = user.id },
                        onLongClick = { longClickedId = user.id },
                    )
                }
            }

            onNodeWithText("Ada Lovelace").performTouchInput { longClick() }

            assertEquals(42L, longClickedId, "long press must raise the delete request")
            assertNull(clickedId, "a long press must not also register as a tap")
        }

    @Test
    fun tappingARowSelectsItWithoutRequestingDeletion() =
        runComposeUiTest {
            var longClickedId: Long? = null
            var clickedId: Long? = null

            setContent {
                SliideTheme {
                    UserRow(
                        user = user,
                        isSelected = false,
                        onClick = { clickedId = user.id },
                        onLongClick = { longClickedId = user.id },
                    )
                }
            }

            onNodeWithText("Ada Lovelace").performClick()

            assertEquals(42L, clickedId)
            assertNull(longClickedId, "a tap must never trigger a destructive action")
        }

    @Test
    fun theRowIsAnnouncedAsOneItemRatherThanFourFragments() =
        runComposeUiTest {
            setContent {
                SliideTheme {
                    UserRow(user = user, isSelected = false, onClick = {}, onLongClick = {})
                }
            }

            // The status dot and the bare timestamp mean nothing read out on their own.
            onNodeWithContentDescription(
                "Ada Lovelace, ada@example.com, Active, added 5 minutes ago",
            ).assertExists()
        }

    @Test
    fun theRowShowsEverythingTheBriefRequires() =
        runComposeUiTest {
            setContent {
                SliideTheme {
                    UserRow(user = user, isSelected = false, onClick = {}, onLongClick = {})
                }
            }

            onNodeWithText("Ada Lovelace").assertExists()
            onNodeWithText("ada@example.com").assertExists()
            onNodeWithText("5 minutes ago").assertExists()
        }
}
