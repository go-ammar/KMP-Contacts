package com.roster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

@Composable
fun UserAvatar(
    initials: String,
    seed: Long,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val palette = avatarPalettes[(seed % avatarPalettes.size).absoluteValue.toInt()]
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(palette))
                // The name is already announced by the row, so the initials are decorative.
                .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            style = if (size >= 64.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        )
    }
}

private val avatarPalettes: List<List<Color>> =
    listOf(
        listOf(Color(0xFF5B6CFF), Color(0xFF8E54E9)),
        listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
        listOf(Color(0xFFF7971E), Color(0xFFFFB75E)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
        listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
    )
