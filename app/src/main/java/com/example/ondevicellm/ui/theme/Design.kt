package com.example.ondevicellm.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Spacing scale. Everything in the UI is a multiple of 4dp so rhythm stays
 * consistent across screens instead of being eyeballed per component.
 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val huge = 40.dp
}

/** Brand gradients, derived from the active color scheme so they follow theming. */
object Gradients {

    /** Primary accent sweep, used for emphasis surfaces and the send button. */
    val accent: Brush
        @Composable @ReadOnlyComposable
        get() = Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.blend(
                    MaterialTheme.colorScheme.tertiary,
                    0.45f,
                ),
            )
        )

    /** Softer version for large fills that sit behind text. */
    val accentSoft: Brush
        @Composable @ReadOnlyComposable
        get() = Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
            )
        )

    /** Page backdrop — a barely-there tint so flat screens gain depth. */
    val page: Brush
        @Composable @ReadOnlyComposable
        get() = Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            )
        )

    /** Halo behind empty-state icons. */
    @Composable
    @ReadOnlyComposable
    fun halo(color: Color): Brush = Brush.radialGradient(
        listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.04f))
    )
}

/** Mixes two colors; [ratio] 0 keeps the receiver, 1 returns [other]. */
fun Color.blend(other: Color, ratio: Float): Color {
    val inverse = 1f - ratio
    return Color(
        red = red * inverse + other.red * ratio,
        green = green * inverse + other.green * ratio,
        blue = blue * inverse + other.blue * ratio,
        alpha = alpha * inverse + other.alpha * ratio,
    )
}

/** Hairline stroke used on every raised surface to define its edge crisply. */
val hairlineColor: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

/**
 * The app's standard raised surface: a tinted fill, a hairline border and a
 * clipped shape. Used instead of Material's Card so borders stay consistent in
 * both light and dark themes.
 */
@Composable
fun Modifier.panel(
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier = this
    .clip(shape)
    .background(color)
    .border(1.dp, hairlineColor, shape)

/** Panel variant filled with a brush rather than a flat color. */
@Composable
fun Modifier.panel(
    brush: Brush,
    shape: Shape = MaterialTheme.shapes.medium,
    borderColor: Color = hairlineColor,
): Modifier = this
    .clip(shape)
    .background(brush)
    .border(1.dp, borderColor, shape)
