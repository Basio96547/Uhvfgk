package com.example.ondevicellm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// A deep indigo/violet identity — reads as "compute" without being another
// generic blue, and keeps enough chroma to stay legible in both themes.
private val Seed = Color(0xFF5B5BD6)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A47C8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3E0FF),
    onPrimaryContainer = Color(0xFF120066),
    secondary = Color(0xFF5D5C71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E0F9),
    onSecondaryContainer = Color(0xFF1A1A2C),
    tertiary = Color(0xFF0F7B6C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA7F2E2),
    onTertiaryContainer = Color(0xFF00201B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCFAFF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFCFAFF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
    surfaceContainer = Color(0xFFF2EFF7),
    surfaceContainerHigh = Color(0xFFECE9F1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3C0FF),
    onPrimary = Color(0xFF221799),
    primaryContainer = Color(0xFF3A35AF),
    onPrimaryContainer = Color(0xFFE3E0FF),
    secondary = Color(0xFFC6C4DD),
    onSecondary = Color(0xFF2F2F42),
    secondaryContainer = Color(0xFF454559),
    onSecondaryContainer = Color(0xFFE3E0F9),
    tertiary = Color(0xFF8BD5C6),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFFA7F2E2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF47464F),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A2930),
)

@Composable
fun OnDeviceLLMTheme(
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
