package com.sliide.kmp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One Material 3 palette, authored once and shared by both platforms. The scheme is built by
 * hand rather than taken from dynamic colour so the app looks identical on iOS and on Android
 * handsets that would otherwise tint it from the wallpaper.
 */
private val BrandIndigo = Color(0xFF4C5BD4)
private val BrandIndigoDark = Color(0xFFB3BCFF)
private val BrandViolet = Color(0xFF7A5AF8)
private val BrandVioletDark = Color(0xFFCDBEFF)
private val AccentTeal = Color(0xFF00897B)
private val AccentTealDark = Color(0xFF6FD9C8)
private val DangerRed = Color(0xFFBA1A1A)
private val DangerRedDark = Color(0xFFFFB4AB)

private val LightScheme =
    lightColorScheme(
        primary = BrandIndigo,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDFE1FF),
        onPrimaryContainer = Color(0xFF00105C),
        secondary = BrandViolet,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9DDFF),
        onSecondaryContainer = Color(0xFF24005A),
        tertiary = AccentTeal,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFB2DFDB),
        onTertiaryContainer = Color(0xFF00201C),
        error = DangerRed,
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFBF8FF),
        onBackground = Color(0xFF1B1B21),
        surface = Color(0xFFFBF8FF),
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE3E1EC),
        onSurfaceVariant = Color(0xFF46464F),
        outline = Color(0xFF777680),
        outlineVariant = Color(0xFFC7C5D0),
    )

/*
 * Dark container roles are deliberately low-chroma. The first pass carried the light scheme's
 * saturation straight across, which turned a selected list row into a solid violet block -
 * loud rather than considered. In M3 a dark container is a dim, desaturated surface that the
 * "on" colour sits brightly on top of, not a restatement of the brand colour.
 */
private val DarkScheme =
    darkColorScheme(
        primary = BrandIndigoDark,
        onPrimary = Color(0xFF1A2678),
        primaryContainer = Color(0xFF2A3270),
        onPrimaryContainer = Color(0xFFDFE1FF),
        secondary = BrandVioletDark,
        onSecondary = Color(0xFF3C008F),
        secondaryContainer = Color(0xFF332A4D),
        onSecondaryContainer = Color(0xFFE9DDFF),
        tertiary = AccentTealDark,
        onTertiary = Color(0xFF003731),
        tertiaryContainer = Color(0xFF1E3F3A),
        onTertiaryContainer = Color(0xFFB2DFDB),
        error = DangerRedDark,
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF5C1A1A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF131318),
        onBackground = Color(0xFFE4E1E9),
        surface = Color(0xFF131318),
        onSurface = Color(0xFFE4E1E9),
        surfaceVariant = Color(0xFF2A2A33),
        onSurfaceVariant = Color(0xFFC7C5D0),
        outline = Color(0xFF918F9A),
        outlineVariant = Color(0xFF46464F),
    )

private val AppTypography =
    Typography().run {
        copy(
            headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
            bodySmall = bodySmall.copy(lineHeight = 16.sp),
        )
    }

@Composable
fun SliideTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
