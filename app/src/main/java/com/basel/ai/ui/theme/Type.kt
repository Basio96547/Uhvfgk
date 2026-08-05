package com.basel.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The type scale, in two versions.
 *
 * Latin and Arabic want different typography, and treating one as a translation
 * of the other is how Arabic ends up looking cramped and slightly wrong in an
 * app that is otherwise fine:
 *
 *  - **Letter-spacing must not be negative.** The scale used −0.2sp on
 *    headings to make them feel compact, which is a normal Latin trick and
 *    actively damaging here: Arabic is a *connected* script, and pulling the
 *    letters together muddies the joins that carry the word shape.
 *  - **Arabic needs more leading.** Its ascenders reach higher and its
 *    diacritics sit above and below the line, so the same line height that
 *    looks generous in Latin looks tight in Arabic — and a dot under a ي on
 *    one line nearly touches the letter above it.
 *  - **The line box must not be trimmed.** With the default trim, the very
 *    first line loses the space its tallest mark needs and a هَمزة above an
 *    ألف is clipped by the container edge.
 *
 * So the same scale is generated twice and the theme picks by language.
 */
private fun scale(arabic: Boolean): Typography {
    // Arabic gets roughly 15% more leading, and never negative tracking.
    val lead = if (arabic) 1.15f else 1.0f
    fun leading(sp: Int) = (sp * lead).toInt().sp
    fun tracking(latin: Double) = if (arabic) 0.sp else latin.sp

    // Keeps the first line's ascenders and the last line's descenders, which
    // is what stops Arabic marks being clipped at a container edge.
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    fun style(
        size: Int,
        line: Int,
        weight: FontWeight,
        track: Double = 0.0,
    ) = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = leading(line),
        letterSpacing = tracking(track),
        lineHeightStyle = lineHeightStyle,
    )

    return Typography(
        headlineSmall = style(22, 28, FontWeight.SemiBold, -0.2),
        titleLarge = style(20, 26, FontWeight.SemiBold, -0.1),
        titleMedium = style(16, 22, FontWeight.SemiBold),
        titleSmall = style(14, 20, FontWeight.SemiBold),
        bodyLarge = style(16, 24, FontWeight.Normal, 0.1),
        bodyMedium = style(14, 21, FontWeight.Normal, 0.1),
        bodySmall = style(12, 18, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.Medium),
        labelMedium = style(12, 16, FontWeight.Medium, 0.3),
        labelSmall = style(11, 15, FontWeight.Medium, 0.4),
    )
}

/** Latin. */
val Typography = scale(arabic = false)

/** Arabic: more leading, no negative tracking, untrimmed line boxes. */
val ArabicTypography = scale(arabic = true)

/** Rounder than stock M3 — softer cards and pill-shaped controls. */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
