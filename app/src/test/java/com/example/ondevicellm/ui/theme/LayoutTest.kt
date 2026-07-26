package com.example.ondevicellm.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {

    private val s25Ultra = Layout.REFERENCE_WIDTH_DP to Layout.REFERENCE_HEIGHT_DP
    private val smallPhone = 360 to 780
    private val tablet = 840 to 1200

    // ---- the regression that started this ---------------------------------

    @Test
    fun `five tabs fit on the reference device`() {
        // Four tabs at 16 dp pill padding needed 425 dp of a 395 dp row, so the
        // fifth tab pushed the last one off the edge.
        val (w, _) = s25Ultra
        val rowWidth = Layout.navItemWidth(w, tabs = 5) * 5
        assertTrue(
            "five tabs need ${rowWidth}dp of ${w - 16}dp",
            rowWidth <= w - Layout.NAV_ROW_PADDING_DP * 2,
        )
    }

    @Test
    fun `tabs still fit on a small phone`() {
        val (w, _) = smallPhone
        assertTrue(Layout.navItemWidth(w, 5) * 5 <= w - Layout.NAV_ROW_PADDING_DP * 2)
    }

    @Test
    fun `padding tightens as tabs are added, never past readable`() {
        val (w, _) = s25Ultra
        val four = Layout.navPillPadding(w, 4)
        val five = Layout.navPillPadding(w, 5)
        val eight = Layout.navPillPadding(w, 8)
        assertTrue("more tabs should not be roomier", five <= four)
        assertTrue(eight <= five)
        assertTrue("a pill needs some padding to read as one", eight >= 6)
    }

    @Test
    fun `five tabs meet the touch target here, eight do not`() {
        val (w, _) = s25Ultra
        assertTrue(Layout.navFitsComfortably(w, 5))
        assertTrue(Layout.navItemWidth(w, 5) >= Layout.TOUCH_TARGET_DP)
        // Squeezed items still fit the row — and then get mistapped.
        assertFalse(Layout.navFitsComfortably(w, 8))
    }

    @Test
    fun `five tabs meet the touch target on a small phone too`() {
        assertTrue(Layout.navFitsComfortably(smallPhone.first, 5))
    }

    @Test
    fun `no tabs does not divide by zero`() {
        assertTrue(Layout.navPillPadding(411, 0) > 0)
    }

    // ---- proportions, not constants ---------------------------------------

    @Test
    fun `a bubble is a proportion of the screen, not a fixed 340dp`() {
        val wide = Layout.bubbleMaxWidth(s25Ultra.first)
        val narrow = Layout.bubbleMaxWidth(smallPhone.first)
        assertTrue("wider screen, wider bubble", wide > narrow)
        // The old constant was 96% of a 360dp screen — edge to edge.
        assertTrue("must leave a margin", narrow < smallPhone.first * 0.9f)
        assertTrue(wide < s25Ultra.first)
    }

    @Test
    fun `a bubble never collapses on a very narrow window`() {
        assertTrue(Layout.bubbleMaxWidth(200) >= 220)
    }

    // ---- the shape of this particular phone -------------------------------

    @Test
    fun `the reference device is tall rather than wide`() {
        val (w, h) = s25Ultra
        assertTrue(Layout.isTall(w, h))
        // The point worth remembering: it is no wider than an ordinary phone.
        assertTrue("same width class as a small phone", w - smallPhone.first < 60)
    }

    @Test
    fun `a tablet is not tall`() {
        assertFalse(Layout.isTall(tablet.first, tablet.second))
    }

    @Test
    fun `a zero-width window does not crash the ratio`() {
        assertFalse(Layout.isTall(0, 800))
    }

    @Test
    fun `the touch target meets the platform minimum`() {
        assertTrue(Layout.TOUCH_TARGET_DP >= 48)
    }
}
