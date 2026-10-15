package com.sliide.kmp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sliide.kmp.domain.error.AppError

/** Full-screen failure, used only when there is no cached data to show instead. */
@Composable
fun ErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = error.illustrationGlyph,
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = error.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = error.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onRetry) {
            Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
    }
}

/** Inline banner shown above stale-but-usable data after a failed refresh. */
@Composable
fun StaleDataBanner(
    error: AppError,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(error.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Showing the last data we saved.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onRetry) {
                Icon(AppIcons.Refresh, contentDescription = "Retry")
            }
            IconButton(onClick = onDismiss) {
                Icon(AppIcons.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
fun EmptyState(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("👤", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text("No users yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap the + button to add the first one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onRefresh) { Text("Refresh") }
    }
}

private val AppError.illustrationGlyph: String
    get() =
        when (this) {
            AppError.NoInternet -> "📡"
            AppError.Timeout -> "⏱️"
            AppError.MissingOrInvalidToken -> "🔑"
            else -> "⚠️"
        }

val AppError.title: String
    get() =
        when (this) {
            AppError.NoInternet -> "No internet connection"
            AppError.Timeout -> "That took too long"
            AppError.MissingOrInvalidToken -> "GoRest token missing or rejected"
            AppError.Serialization -> "Unexpected response"
            is AppError.Validation -> "Some details need fixing"
            is AppError.Http -> "Request failed"
            is AppError.Unknown -> "Something went wrong"
        }

val AppError.detail: String
    get() =
        when (this) {
            AppError.NoInternet ->
                "Check your connection and try again. Anything already saved stays available offline."
            AppError.Timeout -> "The server didn't respond in time."
            AppError.MissingOrInvalidToken ->
                "Add gorest.token=<your token> to local.properties, then rebuild. Browsing works without it; creating and deleting do not."
            AppError.Serialization -> "The server replied in a shape the app didn't expect."
            is AppError.Validation -> fieldErrors.values.joinToString(separator = "\n")
            is AppError.Http -> "The server returned HTTP $code${description?.let { " ($it)" }.orEmpty()}."
            is AppError.Unknown -> description ?: "No further detail is available."
        }

/**
 * One-line phrasing for inline use - a form error or a snackbar - where [detail] would be too
 * long. Together with [title] and [detail] this is the only place an [AppError] becomes words.
 */
val AppError.shortMessage: String
    get() =
        when (this) {
            AppError.NoInternet -> "You're offline - connect and try again"
            AppError.Timeout -> "That took too long. Try again"
            AppError.MissingOrInvalidToken ->
                "Add a valid GoRest token to local.properties to create or delete users"
            AppError.Serialization -> "The server sent something unexpected"
            is AppError.Validation -> "Check the highlighted fields"
            is AppError.Http -> "The request failed (HTTP $code)"
            is AppError.Unknown -> description ?: "Something went wrong"
        }

/** "Couldn't delete Ada Lovelace - you're offline". */
fun AppError.deleteFailureMessage(userName: String): String =
    when (this) {
        AppError.NoInternet -> "Couldn't delete $userName - you're offline"
        AppError.Timeout -> "Couldn't delete $userName - the request timed out"
        AppError.MissingOrInvalidToken -> "Couldn't delete $userName - add a valid GoRest token"
        else -> "Couldn't delete $userName"
    }
