package com.basel.ai.ocr

import android.graphics.Bitmap
import android.util.Base64
import com.basel.ai.core.ErrorLog
import com.basel.ai.core.Localization
import com.basel.ai.core.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** What OCR gave back for one page. */
data class OcrResult(val text: String, val problem: String? = null) {
    val ok: Boolean get() = problem == null
}

/**
 * Reads text off a page image.
 *
 * An interface with one implementation today, and that is the point: offline
 * Arabic OCR is a real gap here (see PLAN), and when it is filled the pipeline
 * should not have to change shape to accept it.
 */
interface TextRecognizer {
    /** Whether this recognizer can run at all right now. */
    fun isAvailable(): Boolean

    suspend fun read(page: Bitmap, languageTag: String): OcrResult

    /** Freed between documents; a recognizer may hold a model. */
    fun release() {}
}

/**
 * OCR through Google Cloud Vision.
 *
 * The page image leaves the device. That is stated where the key is entered,
 * not here — but it is the reason this is off unless switched on and needs the
 * user's own key.
 */
class CloudOcrRecognizer(private val apiKey: String) : TextRecognizer {

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    override suspend fun read(page: Bitmap, languageTag: String): OcrResult =
        withContext(Dispatchers.IO) {
            val s = Localization.strings
            CloudOcr.missingSetting(apiKey, s)?.let { return@withContext OcrResult("", it) }

            val encoded = try {
                encode(page)
            } catch (e: Throwable) {
                // Out of memory on a large page is a real outcome, not a bug.
                ErrorLog.report("OCR", "Could not encode page", e, Severity.WARNING)
                return@withContext OcrResult("", e.message ?: s.ocrEncodeFailed)
            }

            val request = CloudOcr.request(apiKey, encoded, languageTag)
            try {
                val body = post(request)
                CloudOcr.errorOf(body)?.let { return@withContext OcrResult("", it) }
                // No text is a valid answer: a page can be a photograph of a
                // wall. Reporting it as a failure would make the whole
                // document look broken.
                OcrResult(CloudOcr.textOf(body).orEmpty())
            } catch (e: Exception) {
                ErrorLog.report("OCR", "Request failed", e, Severity.WARNING)
                OcrResult("", e.message ?: s.ocrRequestFailed)
            }
        }

    /**
     * JPEG rather than PNG.
     *
     * A rendered text page compresses to roughly a tenth of the size, and the
     * quality loss is far below what OCR notices — but it is the difference
     * between a 2 MB upload per page and 200 KB, on a phone connection, forty
     * times over.
     */
    private fun encode(page: Bitmap): String {
        val buffer = ByteArrayOutputStream()
        page.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, buffer)
        return Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
    }

    private fun post(request: OcrRequest): String {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            request.headers.forEach(::setRequestProperty)
        }
        try {
            connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            // The error body carries the provider's own reason, and it is read
            // rather than thrown away: "API key not valid" and "billing not
            // enabled" are different problems with different fixes.
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val JPEG_QUALITY = 80
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
