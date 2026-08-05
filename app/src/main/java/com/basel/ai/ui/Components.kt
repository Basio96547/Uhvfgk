package com.basel.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basel.ai.ui.theme.Space
import com.basel.ai.ui.theme.hairlineColor
import com.basel.ai.ui.theme.panel

/**
 * Small uppercase label that opens a group of related settings. Deliberately
 * quiet — it orients without competing with the content beneath it.
 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = Space.xs, bottom = Space.sm),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Icon in a tinted rounded tile — the leading element of every section. */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Int = 34,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

/**
 * The app's standard content block: icon tile, title, optional subtitle, then
 * body content. Every screen is built from these so pages feel like one system.
 */
@Composable
fun SectionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().panel().padding(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(Space.lg))
        content()
    }
}

/** Label/value line. Values are weighted so they scan as the data. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Space.md))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}

/**
 * Small explanatory text under a control. [isWarning] switches it to the error
 * color for states the user needs to act on.
 */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, isWarning: Boolean = false) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = if (isWarning) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/** Hairline divider used inside cards to separate related rows. */
@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.md)
            .height(1.dp)
            .background(hairlineColor)
    )
}

/** Compact pill for a status, capability or metric. */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    filled: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = if (filled) 0f else 0.22f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (filled) Color.White else color
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Pulsing dot used to signal a live state (model ready, audio playing). */
@Composable
fun LiveDot(
    color: Color,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    size: Int = 8,
) {
    val transition = rememberInfiniteTransition(label = "livedot")
    val alpha by transition.animateFloat(
        initialValue = if (animated) 0.35f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier
            .size(size.dp)
            .alpha(if (animated) alpha else 1f)
            .background(color, CircleShape)
    )
}

/** Three dots fading in sequence while the model generates. */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(6.dp)
                    .alpha(alpha)
                    .background(dotColor, CircleShape)
            )
        }
    }
}

/** Rounded determinate bar. Animates so progress never jumps. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Int = 6,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(320),
        label = "progress",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height.dp)
                .clip(shape)
                .background(color)
        )
    }
}

/** Gradient variant, for the one or two places that deserve extra emphasis. */
@Composable
fun GradientProgressBar(
    fraction: Float,
    brush: Brush,
    modifier: Modifier = Modifier,
    height: Int = 6,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(320),
        label = "gradientProgress",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height.dp)
                .clip(shape)
                .background(brush)
        )
    }
}
