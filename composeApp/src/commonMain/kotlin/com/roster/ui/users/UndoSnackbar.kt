package com.roster.ui.users

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roster.presentation.users.PendingDeletionUiModel

/**
 * The undo affordance: the message on the left, and on the right a single pill holding the
 * progress ring and the action, so the time running out and the way to stop it read as one
 * control.
 *
 * Built on [Surface] rather than Material's `Snackbar`: that component's `action` slot assumes
 * a lone button and draws a composite action straight over the message text.
 *
 * The ring is animated in the UI rather than ticked from state - a bar that moves in
 * one-second jumps reads as broken, and per-frame state updates would recompose the screen. It
 * runs for the window length the ViewModel publishes, so the circle closes at the same moment
 * the deletion commits and the pill leaves.
 */
@Composable
fun UndoSnackbar(
    pending: PendingDeletionUiModel?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = pending != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        // Held after `pending` clears so the exit animation still has something to draw.
        val lastPending = rememberLastNonNull(pending)
        val progress = remember { Animatable(0f) }

        // Keyed on the user so a second deletion restarts the ring from empty.
        LaunchedEffect(lastPending?.user?.id) {
            val window = lastPending?.windowMillis ?: return@LaunchedEffect
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = window.toInt(), easing = LinearEasing),
            )
        }

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    // The undo window closes on a timer, so a screen reader user has to be told
                    // it exists without having to go looking for it.
                    .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 56.dp)
                        .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Deleted ${lastPending?.user?.name.orEmpty()}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                UndoAction(progress = progress.value, onUndo = onUndo)
            }
        }
    }
}

@Composable
private fun UndoAction(
    progress: Float,
    onUndo: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.inversePrimary
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .clickable(onClick = onUndo)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                // The ring is decoration; the whole pill is one "Undo" target.
                .semantics { contentDescription = "Undo delete" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = accent,
            trackColor = accent.copy(alpha = 0.28f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "UNDO",
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
    }
}

@Composable
private fun rememberLastNonNull(value: PendingDeletionUiModel?): PendingDeletionUiModel? {
    val holder = remember { mutableStateOf<PendingDeletionUiModel?>(null) }
    if (value != null) {
        holder.value = value
    }
    return holder.value
}
