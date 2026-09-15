package com.openhands.remote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val OpenHandsLightColorScheme = lightColorScheme(
    primary = OpenHandsColors.lightPrimary,
    onPrimary = OpenHandsColors.lightOnPrimary,
    background = OpenHandsColors.lightBackground,
    surface = OpenHandsColors.lightSurface,
    surfaceVariant = OpenHandsColors.lightSurfaceVariant,
    onSurface = OpenHandsColors.lightOnSurface,
    outline = OpenHandsColors.lightOutline,
    error = OpenHandsColors.error,
)

private val OpenHandsDarkColorScheme = darkColorScheme(
    primary = OpenHandsColors.darkPrimary,
    onPrimary = OpenHandsColors.darkOnPrimary,
    background = OpenHandsColors.darkBackground,
    surface = OpenHandsColors.darkSurface,
    surfaceVariant = OpenHandsColors.darkSurfaceVariant,
    onSurface = OpenHandsColors.darkOnSurface,
    outline = OpenHandsColors.darkOutline,
    error = OpenHandsColors.error,
)

@Composable
fun OpenHandsRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = OpenHandsDarkColorScheme,
        typography = OpenHandsTypography,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = OpenHandsShapes.control,
            small = OpenHandsShapes.control,
            medium = OpenHandsShapes.card,
            large = OpenHandsShapes.card,
            extraLarge = OpenHandsShapes.card,
        ),
        content = content,
    )
}
