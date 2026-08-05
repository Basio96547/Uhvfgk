package com.basel.ai.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePickerTest {

    private fun voice(
        name: String,
        tag: String = "ar-SA",
        quality: Int = 300,
        network: Boolean = false,
    ) = VoiceOption(name, tag, quality, network)

    // ---- the actual complaint: the robotic voice was being chosen ----------

    @Test
    fun `a better installed voice wins over the old default`() {
        val installed = listOf(
            voice("ar-sa-x-legacy", quality = 200),
            voice("ar-sa-x-neural", quality = 500),
        )
        assertEquals("ar-sa-x-neural", VoicePicker.best(installed, "ar-SA")?.name)
    }

    @Test
    fun `an offline voice beats a slightly better online one`() {
        // This app works without a signal; a voice that goes silent offline is
        // worse than one that is a shade flatter and always speaks.
        val installed = listOf(
            voice("offline", quality = 400),
            voice("online", quality = 500, network = true),
        )
        assertEquals("offline", VoicePicker.best(installed, "ar-SA")?.name)
    }

    @Test
    fun `a far better online voice is still worth it`() {
        val installed = listOf(
            voice("offline", quality = 100),
            voice("online", quality = 500, network = true),
        )
        assertEquals("online", VoicePicker.best(installed, "ar-SA")?.name)
    }

    // ---- language and region ----------------------------------------------

    @Test
    fun `only voices for the right language are considered`() {
        val installed = listOf(
            voice("en", tag = "en-US", quality = 500),
            voice("ar", tag = "ar-EG", quality = 200),
        )
        assertEquals("ar", VoicePicker.best(installed, "ar-SA")?.name)
    }

    @Test
    fun `any arabic region qualifies, but the exact one is preferred`() {
        val installed = listOf(
            voice("egypt", tag = "ar-EG", quality = 400),
            voice("saudi", tag = "ar-SA", quality = 400),
        )
        assertEquals("saudi", VoicePicker.best(installed, "ar-SA")?.name)
        // Both are still offered, so the user can pick a dialect they prefer.
        assertEquals(2, VoicePicker.candidatesFor(installed, "ar-SA").size)
    }

    @Test
    fun `language matching ignores case and separator`() {
        assertEquals("ar", VoicePicker.languageOf("AR-SA"))
        assertEquals("ar", VoicePicker.languageOf("ar_EG"))
        assertEquals("ar", VoicePicker.languageOf("ar"))
    }

    // ---- nothing installed -------------------------------------------------

    @Test
    fun `no voice for the language returns nothing rather than the wrong one`() {
        val installed = listOf(voice("en", tag = "en-US", quality = 500))
        assertNull(VoicePicker.best(installed, "ar-SA"))
        assertTrue(VoicePicker.candidatesFor(installed, "ar-SA").isEmpty())
    }

    @Test
    fun `an empty engine returns nothing`() {
        assertNull(VoicePicker.best(emptyList(), "ar-SA"))
    }

    // ---- telling the user a better voice exists ---------------------------

    @Test
    fun `a better voice than the current one is noticed`() {
        val installed = listOf(
            voice("old", quality = 200),
            voice("new", quality = 500),
        )
        assertTrue(VoicePicker.hasBetterThan(installed, "old", "ar-SA"))
        assertFalse(VoicePicker.hasBetterThan(installed, "new", "ar-SA"))
    }

    @Test
    fun `no current choice means the best one is an improvement`() {
        assertTrue(VoicePicker.hasBetterThan(listOf(voice("a")), null, "ar-SA"))
    }

    @Test
    fun `a voice that has since been uninstalled counts as improvable`() {
        assertTrue(VoicePicker.hasBetterThan(listOf(voice("a")), "gone", "ar-SA"))
    }

    @Test
    fun `nothing installed is not an improvement`() {
        assertFalse(VoicePicker.hasBetterThan(emptyList(), null, "ar-SA"))
    }

    // ---- ordering ----------------------------------------------------------

    @Test
    fun `candidates come back best first`() {
        val installed = listOf(
            voice("c", quality = 100),
            voice("a", quality = 500),
            voice("b", quality = 300),
        )
        assertEquals(
            listOf("a", "b", "c"),
            VoicePicker.candidatesFor(installed, "ar-SA").map { it.name },
        )
    }

    @Test
    fun `the order is stable when scores tie`() {
        val installed = listOf(voice("z"), voice("a"))
        assertEquals(
            listOf("a", "z"),
            VoicePicker.candidatesFor(installed, "ar-SA").map { it.name },
        )
    }
}
