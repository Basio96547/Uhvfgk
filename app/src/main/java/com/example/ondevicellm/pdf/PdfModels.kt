package com.example.ondevicellm.pdf

/** Where a page's text came from. The user is told, because it changes how much to trust it. */
enum class TextSource {
    /** Lifted from the PDF's own text layer. Exact, and the common case. */
    EMBEDDED,

    /** Read off a rendered image. Approximate — a scan, or a page of pictures. */
    OCR,

    /** Nothing readable: no text layer, and OCR was unavailable or found nothing. */
    NONE,
}

/** One page, after extraction. */
data class PdfPageText(
    /** 1-based, as printed on the page and as the user will refer to it. */
    val number: Int,
    val text: String,
    val source: TextSource,
    /** Images found embedded on this page, whether or not they were read. */
    val imageCount: Int = 0,
)

/** A document that has been read. */
data class PdfDoc(
    val id: String,
    /** File name as imported, which is what the user recognises it by. */
    val name: String,
    val pageCount: Int,
    val pages: List<PdfPageText>,
    /** Non-null when the document could not be read at all. */
    val problem: String? = null,
) {
    val charCount: Int get() = pages.sumOf { it.text.length }

    val ocrPageCount: Int get() = pages.count { it.source == TextSource.OCR }

    val unreadablePageCount: Int get() = pages.count { it.source == TextSource.NONE }

    val imageCount: Int get() = pages.sumOf { it.imageCount }

    val isReadable: Boolean get() = problem == null && charCount > 0
}

/** A contiguous run of a document, small enough to put in front of a model. */
data class DocChunk(
    val firstPage: Int,
    val lastPage: Int,
    val text: String,
) {
    /** How the page range reads in a citation. */
    fun pageLabel(): String =
        if (firstPage == lastPage) "$firstPage" else "$firstPage-$lastPage"
}
