package dev.codex.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

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
    onSurfaceVariant = Slate,
    error = ErrorSoft,
    errorContainer = Color(0xFFF9DDD8),
    onErrorContainer = ErrorSoft,
    outline = Fog,
    outlineVariant = Fog.copy(alpha = 0.82f),
)

private val CodexDarkScheme = darkColorScheme(
    primary = NightEmber,
    onPrimary = Color(0xFF311104),
    secondary = NightTeal,
    onSecondary = Color(0xFF082420),
    tertiary = NightInk,
    background = NightCanvas,
    onBackground = NightInk,
    surface = NightPanel,
    onSurface = NightInk,
    surfaceVariant = NightPanelStrong,
    onSurfaceVariant = NightSlate,
    error = NightErrorSoft,
    errorContainer = Color(0xFF5C1F18),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = NightFog,
    outlineVariant = NightFog.copy(alpha = 0.88f),
)

@Composable
fun CodexTheme(
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) CodexDarkScheme else CodexLightScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CodexTypography,
        content = content,
    )
}
