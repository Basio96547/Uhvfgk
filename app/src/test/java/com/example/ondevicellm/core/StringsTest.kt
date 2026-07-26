package com.example.ondevicellm.core

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

        // Untranslated proper nouns: these are the same word in both languages.
        val sharedByDesign = setOf(
            "ramPlus", "vulkan", "openCl", "nnapi", "topK", "defaultVoiceTag",
        )

        val untranslated = english.filter { (key, value) ->
            key !in sharedByDesign && value.isNotBlank() && arabic[key] == value
        }
        assertTrue("copied straight from English: ${untranslated.keys}", untranslated.isEmpty())
    }

    @Test
    fun `no arabic string is left in latin script`() {
        val offenders = stringsOf(ArabicStrings).filterKeys {
            it !in setOf("ramPlus", "vulkan", "openCl", "nnapi", "topK", "defaultVoiceTag")
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
}
