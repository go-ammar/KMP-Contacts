package com.roster.ui.users

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roster.presentation.users.UserUiModel
import com.roster.ui.components.UserAvatar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserRow(
    user: UserUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selection is a background tint rather than a card, so the list stays flat and the
    // master-detail pane is the only thing that gains elevation.
    val haptics = LocalHapticFeedback.current

    // A long press that opens a destructive dialog should be felt before it is seen; on a
    // list of near-identical rows it is the only confirmation that the right one was hit.
    val onLongClickWithHaptics = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongClick()
    }

    val background by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        label = "rowBackground",
    )

    Row(
        modifier =
            modifier
                // Outermost so the clickable node merges into this one: the row is announced
                // as a single item, phrased the way a person would say it. The status dot and
                // the bare timestamp mean nothing read out on their own.
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        with(user) { "$name, $email, $statusLabel, added $relativeTimestamp" }
                    selected = isSelected
                }.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = "Delete ${user.name}",
                ).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(initials = user.initials, seed = user.id)

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(isActive = user.isActive)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = user.relativeTimestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StatusDot(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                if (isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            ),
    )
}
