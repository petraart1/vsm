package ru.vsm.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = VsmColorsLight.actionPrimary,
    onPrimary = VsmColorsLight.textOnAccent,
    primaryContainer = VsmColorsLight.actionSecondary,
    onPrimaryContainer = VsmColorsLight.textPrimary,
    secondary = VsmColorsLight.borderStrong,
    onSecondary = VsmColorsLight.textOnAccent,
    background = VsmColorsLight.backgroundCanvas,
    onBackground = VsmColorsLight.textPrimary,
    surface = VsmColorsLight.backgroundSurface,
    onSurface = VsmColorsLight.textPrimary,
    surfaceVariant = VsmColorsLight.backgroundSurfaceRaised,
    onSurfaceVariant = VsmColorsLight.textSecondary,
    error = VsmColorsLight.actionDestructive,
    onError = VsmColorsLight.textOnAccent,
    outline = VsmColorsLight.borderDefault,
    outlineVariant = VsmColorsLight.borderStrong,
)

private val DarkColorScheme = darkColorScheme(
    primary = VsmColorsDark.actionPrimary,
    onPrimary = VsmColorsDark.textOnAccent,
    primaryContainer = VsmColorsDark.actionSecondary,
    onPrimaryContainer = VsmColorsDark.textPrimary,
    secondary = VsmColorsDark.borderStrong,
    onSecondary = VsmColorsDark.textOnAccent,
    background = VsmColorsDark.backgroundCanvas,
    onBackground = VsmColorsDark.textPrimary,
    surface = VsmColorsDark.backgroundSurface,
    onSurface = VsmColorsDark.textPrimary,
    surfaceVariant = VsmColorsDark.backgroundSurfaceRaised,
    onSurfaceVariant = VsmColorsDark.textSecondary,
    error = VsmColorsDark.actionDestructive,
    onError = VsmColorsDark.textOnAccent,
    outline = VsmColorsDark.borderDefault,
    outlineVariant = VsmColorsDark.borderStrong,
)

/**
 * Цвета шкал и статусов обратной связи, отдельно от `MaterialTheme.colorScheme` — им не находится
 * прямого аналога среди ролей M3, а используются они по всему приложению (шкалы, таймеры, ачивки).
 */
object VsmPalette {
    val loyalty: Color
        @Composable get() = if (isSystemInDarkTheme()) VsmColorsDark.loyaltyFillHigh else VsmColorsLight.loyaltyFillHigh

    val safety: Color
        @Composable get() = if (isSystemInDarkTheme()) VsmColorsDark.safetyFillHigh else VsmColorsLight.safetyFillHigh

    val success: Color
        @Composable get() = if (isSystemInDarkTheme()) VsmColorsDark.feedbackSuccess else VsmColorsLight.feedbackSuccess

    val warning: Color
        @Composable get() = if (isSystemInDarkTheme()) VsmColorsDark.feedbackWarning else VsmColorsLight.feedbackWarning

    val danger: Color
        @Composable get() = if (isSystemInDarkTheme()) VsmColorsDark.feedbackDanger else VsmColorsLight.feedbackDanger
}

@Composable
fun VsmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
