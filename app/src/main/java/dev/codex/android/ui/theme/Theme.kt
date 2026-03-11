package dev.codex.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CodexLightScheme = lightColorScheme(
    primary = Ember,
    onPrimary = Mist,
    secondary = Teal,
    onSecondary = Mist,
    tertiary = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Canvas,
    onSurface = Ink,
    surfaceVariant = PanelStrong,
    onSurfaceVariant = Fog,
    error = ErrorSoft,
    errorContainer = Color(0xFFF9DDD8),
    onErrorContainer = ErrorSoft,
)

@Composable
fun CodexTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CodexLightScheme,
        typography = CodexTypography,
        content = content,
    )
}
