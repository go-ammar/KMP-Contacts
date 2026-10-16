package com.roster.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The handful of icons this app uses, declared as shared vectors.
 *
 * `androidx.compose.material.icons` resolves on Android but is not published for the Apple
 * targets, and the multiplatform `material-icons-*` artifacts are deprecated. Five path
 * strings cost less than a deprecated dependency and keep the icon set identical on both
 * platforms. Path data is Material Symbols, 24dp viewport.
 */
object AppIcons {
    val Add: ImageVector by lazy {
        icon("Add", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    }

    val Close: ImageVector by lazy {
        icon(
            "Close",
            "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 " +
                "17.59 19 19 17.59 13.41 12z",
        )
    }

    val Refresh: ImageVector by lazy {
        icon(
            "Refresh",
            "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-8 8s3.58 8 8 8c3.73 0 " +
                "6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 " +
                "6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z",
        )
    }

    val Person: ImageVector by lazy {
        icon(
            "Person",
            "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 " +
                "1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
        )
    }

    val Email: ImageVector by lazy {
        icon(
            "Email",
            "M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2z" +
                "m0 4l-8 5-8-5V6l8 5 8-5v2z",
        )
    }

    /**
     * Fill is black because [androidx.compose.material3.Icon] re-tints with the current
     * content colour; it is never the colour that reaches the screen.
     */
    private fun icon(
        name: String,
        pathData: String,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            ).build()
}
