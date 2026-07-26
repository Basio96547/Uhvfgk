package com.example.ondevicellm.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.ondevicellm.core.AppLanguage
import com.example.ondevicellm.core.AppStrings
import com.example.ondevicellm.core.EnglishStrings

/** The active string table. Read with `LocalStrings.current` anywhere in the UI. */
val LocalStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }

/**
 * Applies [language] to everything drawn inside [content] — both the words and
 * the direction they flow in.
 *
 * Overriding [LocalLayoutDirection] is what makes Arabic a real translation
 * rather than Arabic text in an English shell: every `start`/`end` padding,
 * every row, the navigation bar and the chat bubbles all mirror. It is set here
 * instead of relying on the device locale so the in-app language switch works
 * without asking the user to change their phone's language.
 */
@Composable
fun ProvideLocalization(language: AppLanguage, content: @Composable () -> Unit) {
    val strings = language.resolve()
    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLayoutDirection provides
            if (strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}
