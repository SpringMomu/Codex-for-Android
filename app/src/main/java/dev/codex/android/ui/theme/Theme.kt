package dev.codex.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val CodexLightScheme = lightColorScheme(
    primary = Ember,
    onPrimary = Mist,
    primaryContainer = Color(0xFFFFE2D6),
    onPrimaryContainer = Color(0xFF54200C),
    secondary = Teal,
    onSecondary = Mist,
    secondaryContainer = Color(0xFFD9F2ED),
    onSecondaryContainer = Color(0xFF0A4B43),
    tertiary = Ink,
    onTertiary = Mist,
    tertiaryContainer = Color(0xFFE9EAEC),
    onTertiaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    surfaceVariant = PanelStrong,
    onSurfaceVariant = Slate,
    error = ErrorSoft,
    onError = Mist,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410001),
    outline = Fog,
    outlineVariant = Fog.copy(alpha = 0.82f),
    inverseSurface = Ink,
    inverseOnSurface = Canvas,
    inversePrimary = Color(0xFFFFB597),
)

private val CodexDarkScheme = darkColorScheme(
    primary = NightEmber,
    onPrimary = Color(0xFF311104),
    primaryContainer = Color(0xFF6D2D13),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = NightTeal,
    onSecondary = Color(0xFF082420),
    secondaryContainer = Color(0xFF153F3A),
    onSecondaryContainer = Color(0xFFB9EDE5),
    tertiary = NightInk,
    onTertiary = NightCanvas,
    tertiaryContainer = Color(0xFF303438),
    onTertiaryContainer = NightInk,
    background = NightCanvas,
    onBackground = NightInk,
    surface = NightPanel,
    onSurface = NightInk,
    surfaceVariant = NightPanelStrong,
    onSurfaceVariant = NightSlate,
    error = NightErrorSoft,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1F18),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = NightFog,
    outlineVariant = NightFog.copy(alpha = 0.88f),
    inverseSurface = NightInk,
    inverseOnSurface = NightCanvas,
    inversePrimary = Ember,
)

private val CodexShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
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
        shapes = CodexShapes,
        content = content,
    )
}
