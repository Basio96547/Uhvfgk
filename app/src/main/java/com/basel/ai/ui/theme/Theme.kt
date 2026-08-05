package com.basel.ai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/*
 * Palette: an indigo→violet core with a teal accent.
 *
 * Chosen over the usual "AI blue" so the app reads as a considered product
 * rather than a template, while keeping enough chroma separation between
 * primary (actions), tertiary (reasoning/memory) and error to stay
 * unambiguous at a glance in both themes.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF13005C),
    inversePrimary = Color(0xFFBFBBFF),

    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF181A2C),

    tertiary = Color(0xFF0D9488),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB8F5EA),
    onTertiaryContainer = Color(0xFF00201C),

    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE1DE),
    onErrorContainer = Color(0xFF410004),

    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF1A1B22),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFE4E1EF),
    onSurfaceVariant = Color(0xFF4A4759),
    surfaceTint = Color(0xFF4F46E5),

    outline = Color(0xFF7B788A),
    outlineVariant = Color(0xFFCBC7D9),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3FC),
    surfaceContainer = Color(0xFFF0EDF8),
    surfaceContainerHigh = Color(0xFFEAE7F4),
    surfaceContainerHighest = Color(0xFFE4E1EF),

    inverseSurface = Color(0xFF2F303A),
    inverseOnSurface = Color(0xFFF2EFFA),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5B0FF),
    onPrimary = Color(0xFF231A8E),
    primaryContainer = Color(0xFF3A32B8),
    onPrimaryContainer = Color(0xFFE0E0FF),
    inversePrimary = Color(0xFF4F46E5),

    secondary = Color(0xFFC5C4DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE1E0F9),

    tertiary = Color(0xFF5EE9D4),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFFB8F5EA),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF111116),
    onBackground = Color(0xFFE5E1EE),
    surface = Color(0xFF111116),
    onSurface = Color(0xFFE5E1EE),
    surfaceVariant = Color(0xFF474459),
    onSurfaceVariant = Color(0xFFC9C5D8),
    surfaceTint = Color(0xFFB5B0FF),

    outline = Color(0xFF938FA3),
    outlineVariant = Color(0xFF474459),

    surfaceContainerLowest = Color(0xFF0C0C10),
    surfaceContainerLow = Color(0xFF19191F),
    surfaceContainer = Color(0xFF1D1D24),
    surfaceContainerHigh = Color(0xFF27272F),
    surfaceContainerHighest = Color(0xFF32323B),

    inverseSurface = Color(0xFFE5E1EE),
    inverseOnSurface = Color(0xFF2F303A),
    scrim = Color(0xFF000000),
)

@Composable
fun BaselAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You wallpaper colors on Android 12+. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Keep the status/navigation bar icons legible against the app background.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
