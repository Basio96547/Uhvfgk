package com.example.ondevicellm.ui.theme

/**
 * Layout arithmetic, derived from the window rather than guessed.
 *
 * The reference device is a Galaxy S25 Ultra: **411 × 891 dp**. Note what that
 * is and isn't — at 1440×3120 physical pixels it sounds enormous, but Samsung
 * ships it at density 3.5, so in layout units it is exactly as *wide* as an
 * ordinary phone and unusually *tall*. Designing for "a big screen" by making
 * things wider is therefore the wrong instinct; the room is vertical.
 *
 * That distinction is what broke the navigation bar. Five tabs at the padding
 * four tabs used need 425 dp of a 395 dp row, so the last one was pushed off
 * the edge. Numbers like that should fail a test, not a user's eye, so the
 * arithmetic lives here as plain functions.
 *
 * Everything is computed from the real window size, so tuning for this phone
 * does not mean breaking every other one.
 */
object Layout {

    /** The reference device, for tests and for reading the numbers below. */
    const val REFERENCE_WIDTH_DP = 411
    const val REFERENCE_HEIGHT_DP = 891

    /** Material's minimum touch target. Below this, taps start missing. */
    const val TOUCH_TARGET_DP = 48

    /** Padding at the outer edges of the navigation row. */
    const val NAV_ROW_PADDING_DP = 8

    /** Icon drawn inside a navigation item. */
    const val NAV_ICON_DP = 22

    /**
     * Horizontal padding inside a navigation item's selected pill.
     *
     * Shrinks as tabs are added, because the row's width does not grow. Never
     * below 6 dp, where the pill stops reading as a pill.
     */
    fun navPillPadding(widthDp: Int, tabs: Int): Int {
        if (tabs <= 0) return 14
        val perItem = (widthDp - NAV_ROW_PADDING_DP * 2) / tabs
        // Leave a couple of dp of breathing room between neighbouring pills.
        val available = (perItem - NAV_ICON_DP - 4) / 2
        return available.coerceIn(6, 14)
    }

    /** Width of one navigation item's pill at the padding [navPillPadding] gives. */
    fun navItemWidth(widthDp: Int, tabs: Int): Int =
        navPillPadding(widthDp, tabs) * 2 + NAV_ICON_DP

    /**
     * True when [tabs] items still meet the touch target.
     *
     * Not "do they fit" — squeezed items fit and then get mistapped. The line
     * is [TOUCH_TARGET_DP], which five tabs clear here and eight do not.
     */
    fun navFitsComfortably(widthDp: Int, tabs: Int): Boolean =
        navItemWidth(widthDp, tabs) >= TOUCH_TARGET_DP

    /**
     * Widest a chat bubble may be.
     *
     * A proportion, not a constant: 340 dp was 83% of a 411 dp screen and 96%
     * of a small one, so the same number read as roomy on one phone and
     * edge-to-edge on another.
     */
    fun bubbleMaxWidth(widthDp: Int): Int = (widthDp * 0.84f).toInt().coerceAtLeast(220)

    /**
     * Widest a column of prose or settings should get.
     *
     * Only bites on tablets, foldables and DeX, where a full-width line is
     * tiring to read. A phone is always narrower than this.
     */
    fun contentMaxWidth(widthDp: Int): Int = minOf(widthDp, 640)

    /**
     * True when the window is tall enough that a short empty state floating at
     * the top looks stranded rather than deliberate.
     */
    fun isTall(widthDp: Int, heightDp: Int): Boolean =
        widthDp > 0 && heightDp.toFloat() / widthDp >= 1.9f
}
