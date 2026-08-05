package com.basel.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Guards the translation itself.
 *
 * The interface already makes a *missing* Arabic string a compile error. What
 * it cannot catch is a string that was copied across untranslated, which is how
 * half-localised apps happen — so that is checked here, by reflection over
 * every property rather than a list somebody has to remember to update.
 */
class StringsTest {

    private val arabicRange = '؀'..'ۿ'

    /**
     * Strings that are the same word in both languages: brand names and
     * technical identifiers. Transliterating "Azure Neural" into Arabic would
     * make it harder to match against the provider's own console, not easier.
     *
     * One list, used by both checks below — two copies drift.
     */
    private val sharedByDesign = setOf(
        "ramPlus", "vulkan", "openCl", "nnapi", "topK", "defaultVoiceTag",
        "cloudAzure", "cloudElevenLabs",
    )

    /** Every string property on [AppStrings], read off an instance. */
    private fun stringsOf(instance: AppStrings): Map<String, String> =
        AppStrings::class.declaredMemberProperties
            .filter { it.returnType.classifier == String::class }
            .associate { property ->
                property.isAccessible = true
                property.name to (property.getter.call(instance) as String)
            }

    @Test
    fun `arabic translates every string`() {
        val english = stringsOf(EnglishStrings)
        val arabic = stringsOf(ArabicStrings)
        assertEquals(english.keys, arabic.keys)

        val untranslated = english.filter { (key, value) ->
            key !in sharedByDesign && value.isNotBlank() && arabic[key] == value
        }
        assertTrue("copied straight from English: ${untranslated.keys}", untranslated.isEmpty())
    }

    @Test
    fun `no arabic string is left in latin script`() {
        val offenders = stringsOf(ArabicStrings).filterKeys {
            it !in sharedByDesign
        }.filter { (_, value) ->
            value.isNotBlank() && value.none { it in arabicRange }
        }
        assertTrue("no Arabic characters in: ${offenders.keys}", offenders.isEmpty())
    }

    @Test
    fun `direction and voice tag match the language`() {
        assertTrue(ArabicStrings.isRtl)
        assertFalse(EnglishStrings.isRtl)
        assertEquals("ar-SA", ArabicStrings.defaultVoiceTag)
        assertEquals("en-US", EnglishStrings.defaultVoiceTag)
    }

    @Test
    fun `only arabic asks the model to switch language`() {
        // English is the model's default; telling it so wastes context.
        assertEquals("", EnglishStrings.replyLanguageInstruction)
        assertTrue(ArabicStrings.replyLanguageInstruction.isNotBlank())
    }

    @Test
    fun `explicit choices ignore the phone locale`() {
        assertEquals(ArabicStrings, AppLanguage.ARABIC.resolve(Locale.US))
        assertEquals(EnglishStrings, AppLanguage.ENGLISH.resolve(Locale("ar", "SA")))
    }

    @Test
    fun `auto follows the phone locale`() {
        assertEquals(ArabicStrings, AppLanguage.SYSTEM.resolve(Locale("ar", "SA")))
        assertEquals(ArabicStrings, AppLanguage.SYSTEM.resolve(Locale("ar", "EG")))
        assertEquals(EnglishStrings, AppLanguage.SYSTEM.resolve(Locale.US))
        assertEquals(EnglishStrings, AppLanguage.SYSTEM.resolve(Locale.FRANCE))
    }

    @Test
    fun `language names are written in their own language`() {
        // A picker that says "Arabic" in English is no use to someone who can
        // only read Arabic — which is exactly who needs the picker.
        assertEquals("العربية", AppLanguage.ARABIC.label)
        assertEquals("English", AppLanguage.ENGLISH.label)
    }

    @Test
    fun `arabic plurals are not a bolted-on s`() {
        assertNotEquals(ArabicStrings.recordedCount(1), ArabicStrings.recordedCount(2))
        assertNotEquals(ArabicStrings.recordedCount(2), ArabicStrings.recordedCount(5))
        assertNotEquals(ArabicStrings.foundModels(1), ArabicStrings.foundModels(2))
    }

    @Test
    fun `the routing line joins with the right conjunction`() {
        val line = ArabicStrings.routeLine("أساس", listOf("بحث", "تفكير"))
        assertTrue(line.contains("بحث"))
        assertTrue(line.contains("تفكير"))
        assertEquals("أساس", ArabicStrings.routeLine("أساس", emptyList()))
    }

    @Test
    fun `Localization tracks the chosen language`() {
        Localization.apply(AppLanguage.ARABIC)
        assertEquals(ArabicStrings, Localization.strings)
        Localization.apply(AppLanguage.ENGLISH)
        assertEquals(EnglishStrings, Localization.strings)
    }

    // ---- the house rules the model is given -------------------------------

    @Test
    fun `the guidance is short enough for a small model to follow`() {
        // A long system prompt confuses a small model and eats the context it
        // needs for the conversation itself. Roughly 200 tokens is the ceiling.
        listOf(EnglishStrings, ArabicStrings).forEach { strings ->
            assertTrue(strings.assistantGuidance.length in 200..900)
        }
    }

    @Test
    fun `the guidance covers what a small model gets wrong on its own`() {
        // Each of these is a failure mode a 4B model shows and a large one
        // doesn't: padding, confabulating, guessing at an ambiguous request,
        // and ignoring what was already said.
        val en = EnglishStrings.assistantGuidance.lowercase()
        listOf("do not know", "invent", "short", "ambiguous", "earlier").forEach {
            assertTrue("guidance should mention \"$it\"", en.contains(it))
        }
    }

    @Test
    fun `the arabic guidance is written in arabic, not translated word for word`() {
        val ar = ArabicStrings.assistantGuidance
        assertTrue(ar.any { it in arabicRange })
        // It also tells the model to read dialect and answer in MSA, which the
        // English one has no reason to say.
        assertTrue(ar.contains("اللهجات"))
        assertTrue(ar.contains("الفصحى"))
    }

    @Test
    fun `both guidance texts are rule lists, not prose`() {
        listOf(EnglishStrings, ArabicStrings).forEach { strings ->
            val bullets = strings.assistantGuidance.lines().count { it.trimStart().startsWith("-") }
            assertTrue("expected a list of rules, got $bullets", bullets >= 6)
        }
    }
}
