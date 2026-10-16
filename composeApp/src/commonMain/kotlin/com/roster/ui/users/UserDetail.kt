package com.roster.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roster.presentation.users.UserUiModel
import com.roster.ui.components.UserAvatar

/**
 * The right-hand pane on wide layouts: the detail centred in the available space, or a prompt
 * when nothing is selected.
 */
@Composable
fun UserDetailPane(
    user: UserUiModel?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (user == null) {
            Text(
                text = "Select a user to see their details",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            UserDetailContent(
                user = user,
                onDelete = onDelete,
                contentPadding = contentPadding,
            )
        }
    }
}

/**
 * The detail itself, laid out top-down and scrollable.
 *
 * Deliberately not vertically centred: centring fixed-height content inside a bottom sheet
 * pushed the delete action off the bottom of the screen. Sizing to its content instead means
 * the sheet opens tall enough to show every row including the action, and the scroll is there
 * for short screens and large font scales.
 */
@Composable
fun UserDetailContent(
    user: UserUiModel,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier =
            modifier
                // Capped and centred: a label/value row stretched across a 1300dp tablet pane
                // reads as broken typography, and the full-width version ran underneath the
                // floating action button.
                .widthIn(max = DetailContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 32.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UserAvatar(initials = user.initials, seed = user.id, size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(user.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        DetailRow(label = "Status", value = user.statusLabel)
        DetailRow(label = "Gender", value = user.genderLabel)
        DetailRow(label = "Added", value = user.relativeTimestamp)
        DetailRow(label = "ID", value = user.id.toString())
        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = onDelete,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
        ) {
            Text("Delete user")
        }
    }
}

/** Wide enough for an email address, narrow enough to stay readable on a tablet. */
private val DetailContentMaxWidth = 460.dp

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
