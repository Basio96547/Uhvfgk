package com.basel.ai.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.basel.ai.core.ErrorLog
import com.basel.ai.core.Severity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pulls text, images and rendered pages out of a PDF.
 *
 * Two libraries rather than one, because neither does both jobs:
 *
 *  - **PDFBox** reads the text layer and the embedded image objects. Android
 *    has no API for either — `PdfRenderer` draws pages and cannot tell you a
 *    single character that is on them.
 *  - **Android's `PdfRenderer`** rasterises a page. PDFBox *can* render, but
 *    its Android port does it in software with a partial graphics stack, and
 *    on a phone it is both slower and less faithful than the platform
 *    renderer, which is hardware-accelerated and is what every PDF viewer on
 *    the device already uses.
 *
 * So text comes from PDFBox and pixels come from the platform.
 */
class PdfExtractor(private val context: Context) {

    /** PDFBox needs its font resources unpacked once per process. */
    private fun ensureLoaded() {
        if (!loaded) {
            PDFBoxResourceLoader.init(context.applicationContext)
            loaded = true
        }
    }

    /** Page count without reading anything else — used to size the progress bar. */
    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) {
        runCatching {
            ensureLoaded()
            PDDocument.load(file).use { it.numberOfPages }
        }.getOrDefault(0)
    }

    /**
     * The text layer, page by page.
     *
     * One `PDFTextStripper` pass per page rather than one for the whole
     * document: page boundaries are the citations the user checks, and a
     * single call returns one string with nothing to align them to.
     *
     * @param onProgress called with (page, total) so a hundred-page file does
     *   not look like a hang.
     */
    suspend fun extractText(
        file: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<PdfPageText> = withContext(Dispatchers.IO) {
        ensureLoaded()
        val out = mutableListOf<PdfPageText>()
        PDDocument.load(file).use { document ->
            val total = document.numberOfPages
            val stripper = PDFTextStripper()
            // Sorting by position is what makes multi-column pages readable.
            // Without it a two-column paper comes out interleaved line by line
            // and is worthless to a model and to a person alike.
            stripper.sortByPosition = true

            for (index in 0 until total) {
                stripper.startPage = index + 1
                stripper.endPage = index + 1
                val text = runCatching { stripper.getText(document) }.getOrElse { error ->
                    // A single broken page should not lose the other ninety.
                    ErrorLog.report(
                        "PDF", "Page ${index + 1} text failed", error, Severity.WARNING
                    )
                    ""
                }
                val images = runCatching { countImages(document.getPage(index)) }.getOrDefault(0)
                val usable = TextLayerQuality.isUsable(text)
                out += PdfPageText(
                    number = index + 1,
                    text = if (usable) tidy(text) else "",
                    source = if (usable) TextSource.EMBEDDED else TextSource.NONE,
                    imageCount = images,
                )
                onProgress(index + 1, total)
            }
        }
        out
    }

    /** How many image objects a page draws. Tells a scan from a page of prose. */
    private fun countImages(page: PDPage): Int {
        val resources = page.resources ?: return 0
        var count = 0
        for (name in resources.xObjectNames) {
            val xObject: PDXObject? = runCatching { resources.getXObject(name) }.getOrNull()
            if (xObject is PDImageXObject) count++
        }
        return count
    }

    /**
     * Renders one page to a bitmap for OCR.
     *
     * The scale matters more than it looks: OCR accuracy falls off a cliff
     * below roughly 200 dpi for body text, and rises very little above 300.
     * `PdfRenderer` measures pages in points, so the multiplier is dpi/72.
     */
    suspend fun renderPage(file: File, pageNumber: Int, dpi: Int = OCR_DPI): Bitmap? =
        withContext(Dispatchers.IO) {
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null
            try {
                descriptor = ParcelFileDescriptor.open(
                    file, ParcelFileDescriptor.MODE_READ_ONLY
                )
                renderer = PdfRenderer(descriptor)
                if (pageNumber < 1 || pageNumber > renderer.pageCount) return@withContext null

                page = renderer.openPage(pageNumber - 1)
                val scale = dpi / POINTS_PER_INCH
                val width = (page.width * scale).toInt().coerceIn(1, MAX_PIXELS)
                val height = (page.height * scale).toInt().coerceIn(1, MAX_PIXELS)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // PdfRenderer draws only what the page paints, so an unpainted
                // background stays transparent — which encodes to black in a
                // JPEG and hands OCR a blank sheet.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (e: Throwable) {
                ErrorLog.report("PDF", "Could not render page $pageNumber", e, Severity.WARNING)
                null
            } finally {
                // Explicit rather than `use`: PdfRenderer.Page is AutoCloseable
                // and not Closeable, and which of those Kotlin's `use` accepts
                // depends on the stdlib variant on the classpath.
                runCatching { page?.close() }
                runCatching { renderer?.close() }
                runCatching { descriptor?.close() }
            }
        }

    /**
     * Saves the images embedded in a page.
     *
     * Separate from OCR on purpose: a page can carry a diagram worth keeping
     * even when its text layer is perfectly good, and "extract the images"
     * was asked for as its own thing, not only as an OCR fallback.
     *
     * @return the files written, in page order.
     */
    suspend fun extractImages(
        file: File,
        into: File,
        maxImages: Int = MAX_IMAGES,
    ): List<File> = withContext(Dispatchers.IO) {
        ensureLoaded()
        into.mkdirs()
        val written = mutableListOf<File>()

        runCatching {
            PDDocument.load(file).use { document ->
                for (index in 0 until document.numberOfPages) {
                    if (written.size >= maxImages) break
                    val resources = document.getPage(index).resources ?: continue
                    for (name in resources.xObjectNames) {
                        if (written.size >= maxImages) break
                        val xObject = runCatching { resources.getXObject(name) }.getOrNull()
                        if (xObject !is PDImageXObject) continue

                        val bitmap = runCatching { xObject.image }.getOrNull() ?: continue
                        // Icons, rules and spacer pixels are the majority of
                        // image objects in a typical document and are never
                        // what anyone meant by "the images in the PDF".
                        if (bitmap.width < MIN_IMAGE_SIDE || bitmap.height < MIN_IMAGE_SIDE) {
                            continue
                        }
                        val target = File(into, "p${index + 1}-${written.size + 1}.png")
                        runCatching {
                            target.outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            written += target
                        }
                    }
                }
            }
        }.onFailure {
            ErrorLog.report("PDF", "Could not extract images", it, Severity.WARNING)
        }
        written
    }

    /**
     * Cleans up extracted text.
     *
     * PDF text extraction produces a lot of noise that costs context and
     * teaches a model nothing: hard-wrapped lines, hyphenated word breaks, and
     * runs of blank lines where the layout had whitespace.
     */
    internal fun tidy(raw: String): String = raw
        .replace(' ', ' ')
        // A word broken across a line break: "inter-\nnational".
        .replace(Regex("(\\p{L})-\\n(\\p{L})"), "$1$2")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .lines()
        .joinToString("\n") { it.trim() }
        .trim()

    companion object {
        private var loaded = false

        const val OCR_DPI = 250
        private const val POINTS_PER_INCH = 72f

        /** Guards against a poster-sized page turning into a 400 MB bitmap. */
        private const val MAX_PIXELS = 4_000

        private const val MAX_IMAGES = 60

        /** Below this, an image object is a rule, an icon or a spacer. */
        private const val MIN_IMAGE_SIDE = 64
    }
}
