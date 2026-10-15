package com.sliide.kmp.ui.users

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sliide.kmp.presentation.users.PendingDeletionUiModel
import com.sliide.kmp.presentation.users.UserUiModel
import com.sliide.kmp.ui.components.UserListSkeleton
import com.sliide.kmp.ui.theme.SliideTheme

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
    SliideTheme {
        UserRow(user = sampleUser, isSelected = false, onClick = {}, onLongClick = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UserRowSelectedPreview() {
    SliideTheme(useDarkTheme = true) {
        UserRow(user = sampleUser, isSelected = true, onClick = {}, onLongClick = {})
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun UserDetailPreview() {
    SliideTheme {
        UserDetailContent(user = sampleUser, onDelete = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SkeletonPreview() {
    SliideTheme {
        UserListSkeleton(itemCount = 4)
    }
}

@Preview(showBackground = true)
@Composable
private fun UndoSnackbarPreview() {
    SliideTheme {
        Box(Modifier.fillMaxSize().size(width = 400.dp, height = 120.dp)) {
            UndoSnackbar(
                pending = PendingDeletionUiModel(user = sampleUser, windowMillis = 5_000),
                onUndo = {},
            )
        }
    }
}
