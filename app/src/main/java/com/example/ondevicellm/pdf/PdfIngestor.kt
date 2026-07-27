package com.example.ondevicellm.pdf

import android.content.Context
import android.net.Uri
import com.example.ondevicellm.core.ErrorLog
import com.example.ondevicellm.core.Localization
import com.example.ondevicellm.ocr.TextRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** What the import is doing, so a five-minute OCR run is not a frozen screen. */
data class PdfProgress(
    val name: String,
    val page: Int,
    val total: Int,
    /** Non-null while OCR is running, which is the slow part worth naming. */
    val stage: String? = null,
) {
    val fraction: Float get() = if (total <= 0) 0f else page.toFloat() / total
}

/**
 * Reads a PDF end to end.
 *
 * The order is the whole design, and it is ordered by what is cheap and exact
 * before what is slow and approximate:
 *
 *  1. **Text layer.** Almost every PDF that was produced rather than scanned
 *     has one, it is exact, it costs nothing, and it works in Arabic without
 *     any of the caveats OCR carries.
 *  2. **Quality check** per page — see [TextLayerQuality]. A page whose text
 *     layer is missing *or broken* is treated as an image.
 *  3. **Render and read** those pages only. Rendering all forty pages of a
 *     document to run OCR over the two that needed it would turn a two-second
 *     import into a two-minute one for no gain.
 *  4. **Embedded images** are pulled out regardless, because a diagram on a
 *     page with perfectly good text is still something the user asked to have.
 */
class PdfIngestor(
    context: Context,
    private val library: PdfLibrary,
) {

    private val extractor = PdfExtractor(context)

    private val _progress = MutableStateFlow<PdfProgress?>(null)
    val progress: StateFlow<PdfProgress?> = _progress.asStateFlow()

    /**
     * Imports and reads [uri].
     *
     * @param recognizer used only for pages with no usable text layer. Null,
     *   or unavailable, means those pages stay unread — and say so, rather
     *   than being quietly dropped.
     */
    suspend fun ingest(
        uri: Uri,
        recognizer: TextRecognizer?,
        languageTag: String,
    ): PdfDoc? = withContext(Dispatchers.IO) {
        val s = Localization.strings
        val imported = library.import(uri) ?: return@withContext null
        val (id, name) = imported
        val file = library.fileOf(id)

        try {
            _progress.value = PdfProgress(name, 0, 1, s.pdfReadingText)

            var pages = extractor.extractText(file) { page, total ->
                _progress.value = PdfProgress(name, page, total, s.pdfReadingText)
            }

            if (pages.isEmpty()) {
                val doc = PdfDoc(id, name, 0, emptyList(), problem = s.pdfNoPages)
                library.put(doc)
                return@withContext doc
            }

            pages = runOcrWhereNeeded(file, name, pages, recognizer, languageTag)

            // Images are their own deliverable, not only an OCR fallback.
            _progress.value = PdfProgress(name, pages.size, pages.size, s.pdfExtractingImages)
            runCatching { extractor.extractImages(file, library.imageDirOf(id)) }

            val doc = PdfDoc(
                id = id,
                name = name,
                pageCount = pages.size,
                pages = pages,
                problem = if (pages.none { it.text.isNotBlank() }) s.pdfNothingReadable else null,
            )
            library.put(doc)
            doc
        } catch (e: CancellationException) {
            library.remove(id)
            throw e
        } catch (e: Throwable) {
            ErrorLog.report("PDF", "Could not read \"$name\"", e)
            val doc = PdfDoc(id, name, 0, emptyList(), problem = e.message ?: s.pdfCouldNotRead)
            library.put(doc)
            doc
        } finally {
            _progress.value = null
        }
    }

    /** Renders and reads only the pages that need it. */
    private suspend fun runOcrWhereNeeded(
        file: java.io.File,
        name: String,
        pages: List<PdfPageText>,
        recognizer: TextRecognizer?,
        languageTag: String,
    ): List<PdfPageText> {
        val needing = pages.filter { it.source == TextSource.NONE }
        if (needing.isEmpty()) return pages
        if (recognizer == null || !recognizer.isAvailable()) return pages

        val s = Localization.strings
        val byNumber = pages.associateBy { it.number }.toMutableMap()
        var done = 0

        for (page in needing) {
            _progress.value = PdfProgress(name, ++done, needing.size, s.pdfReadingImages)

            val bitmap = extractor.renderPage(file, page.number) ?: continue
            val result = try {
                recognizer.read(bitmap, languageTag)
            } finally {
                bitmap.recycle()
            }

            if (result.text.isNotBlank()) {
                byNumber[page.number] = page.copy(
                    text = extractor.tidy(result.text),
                    source = TextSource.OCR,
                )
            } else if (!result.ok) {
                // One failure is usually every failure — a wrong key, no
                // signal — so stop rather than make forty identical requests.
                ErrorLog.report(
                    "PDF",
                    "OCR stopped at page ${page.number}: ${result.problem}",
                    severity = com.example.ondevicellm.core.Severity.WARNING,
                )
                break
            }
        }
        return pages.map { byNumber[it.number] ?: it }
    }
}
