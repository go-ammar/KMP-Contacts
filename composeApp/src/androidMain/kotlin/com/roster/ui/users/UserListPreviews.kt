package com.roster.ui.users

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.roster.presentation.users.PendingDeletionUiModel
import com.roster.presentation.users.UserUiModel
import com.roster.ui.components.UserListSkeleton
import com.roster.ui.theme.RosterTheme

private val sampleUser =
    UserUiModel(
        id = 7,
        name = "Ada Lovelace",
        email = "ada.lovelace@example.com",
        initials = "AL",
        relativeTimestamp = "5 minutes ago",
        isActive = true,
        genderLabel = "Female",
    )

@Preview(showBackground = true)
@Composable
private fun UserRowPreview() {
    RosterTheme {
        UserRow(user = sampleUser, isSelected = false, onClick = {}, onLongClick = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UserRowSelectedPreview() {
    RosterTheme(useDarkTheme = true) {
        UserRow(user = sampleUser, isSelected = true, onClick = {}, onLongClick = {})
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun UserDetailPreview() {
    RosterTheme {
        UserDetailContent(user = sampleUser, onDelete = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SkeletonPreview() {
    RosterTheme {
        UserListSkeleton(itemCount = 4)
    }
}

@Preview(showBackground = true)
@Composable
private fun UndoSnackbarPreview() {
    RosterTheme {
        Box(Modifier.fillMaxSize().size(width = 400.dp, height = 120.dp)) {
            UndoSnackbar(
                pending = PendingDeletionUiModel(user = sampleUser, windowMillis = 5_000),
                onUndo = {},
            )
        }
    }
}
