package com.example.ondevicellm.studio

import com.example.ondevicellm.core.AppStrings

/**
 * Builds the turn sent to the model for a Studio request.
 *
 * Studio turns are **stateless**: the file is the conversation. Each request
 * carries the current code and the change wanted, and the session is reset
 * first. Threading it as a normal chat instead would fill the context with
 * three copies of the same page after two edits, and the model would then be
 * editing a version it could no longer see the top of.
 */
object StudioPrompt {

    /** Longest file we will send back for editing, in characters. */
    const val MAX_CODE_CHARS = 12_000

    fun build(currentCode: String, request: String, s: AppStrings): String {
        val code = currentCode.trim()
        if (code.isEmpty()) return s.studioCreateTurn(request.trim())
        return s.studioEditTurn(code.take(MAX_CODE_CHARS), request.trim())
    }

    /** True when the file has grown past what can be sent back for editing. */
    fun isTooLargeToEdit(code: String): Boolean = code.length > MAX_CODE_CHARS
}
