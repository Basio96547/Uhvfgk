package com.example.ondevicellm.ocr

import com.example.ondevicellm.agent.MiniJson
import com.example.ondevicellm.core.AppStrings

/** Everything needed to make one OCR request. */
data class OcrRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

/**
 * Reading text off a page image, through a hosted service.
 *
 * One provider, deliberately. Google Cloud Vision's `DOCUMENT_TEXT_DETECTION`
 * is a single synchronous POST with an API key, needs no SDK at all, and reads
 * Arabic properly. The alternatives are worse for this app rather than merely
 * different: Azure's Image Analysis `read` covers Latin scripts, and its
 * Arabic support lives in Document Intelligence, which is a submit-then-poll
 * API — twice the code for a worse answer in the language that matters most
 * here.
 *
 * Pure. The URL, the JSON, the base64 envelope and the response shape are the
 * parts that break silently, and none of them needs a key or a network to
 * test.
 */
object CloudOcr {

    const val ENDPOINT = "https://vision.googleapis.com/v1/images:annotate"

    /**
     * Language hints, by the app's own language tag.
     *
     * Vision detects script on its own, but a hint measurably helps Arabic:
     * without it, Arabic pages with any Latin in them (page numbers, a URL in
     * a footer) sometimes come back transliterated.
     */
    fun languageHints(languageTag: String): List<String> {
        val language = languageTag.substringBefore('-').lowercase()
        return when (language) {
            "ar" -> listOf("ar", "en")
            "" -> listOf("en")
            else -> listOf(language, "en")
        }
    }

    /**
     * @param base64Image the page image, already base64-encoded — the encoder
     *   is on Android and this stays testable without it.
     */
    fun request(apiKey: String, base64Image: String, languageTag: String): OcrRequest =
        OcrRequest(
            // The key goes in the query string because that is the only auth
            // this endpoint takes without a service account and a signed JWT.
            url = "$ENDPOINT?key=${apiKey.trim()}",
            headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
            body = buildString {
                append("""{"requests":[{"image":{"content":"""")
                append(base64Image)
                // DOCUMENT_TEXT_DETECTION rather than TEXT_DETECTION: the
                // former is tuned for dense pages and returns them in reading
                // order, which for a scanned document is the whole point.
                append(""""},"features":[{"type":"DOCUMENT_TEXT_DETECTION"}]""")
                append(""","imageContext":{"languageHints":[""")
                append(languageHints(languageTag).joinToString(",") { "\"$it\"" })
                append("""]}}]}""")
            },
        )

    /**
     * The page text out of a Vision response.
     *
     * Returns null when the response is an error or has no text — the two are
     * told apart by [errorOf], because "this page is blank" and "your key is
     * wrong" must not look the same to the user.
     */
    fun textOf(json: String): String? {
        val root = MiniJson.parseObject(json) ?: return null
        val responses = root["responses"] as? List<*> ?: return null
        val first = responses.firstOrNull() as? Map<*, *> ?: return null

        @Suppress("UNCHECKED_CAST")
        val annotation = first["fullTextAnnotation"] as? Map<String, Any?>
        val text = annotation?.get("text") as? String
        return text?.takeIf { it.isNotBlank() }
    }

    /** The provider's own error message, when there is one. */
    fun errorOf(json: String): String? {
        val root = MiniJson.parseObject(json) ?: return null

        // Two shapes: a top-level error for auth and quota, and a per-request
        // error for a bad image. Both matter and they are not the same fault.
        (root["error"] as? Map<*, *>)?.let { error ->
            (error["message"] as? String)?.let { return it }
        }
        val responses = root["responses"] as? List<*> ?: return null
        val first = responses.firstOrNull() as? Map<*, *> ?: return null
        (first["error"] as? Map<*, *>)?.let { error ->
            (error["message"] as? String)?.let { return it }
        }
        return null
    }

    /** Non-null when the settings are not complete enough to try. */
    fun missingSetting(apiKey: String?, s: AppStrings): String? =
        if (apiKey.isNullOrBlank()) s.ocrNeedsKey else null
}
