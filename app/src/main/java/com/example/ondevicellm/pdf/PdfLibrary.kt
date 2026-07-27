package com.example.ondevicellm.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.ondevicellm.core.ErrorLog
import com.example.ondevicellm.core.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The documents that have been read, and their text.
 *
 * Extraction is expensive — minutes, for a scanned document that had to go
 * through OCR a page at a time — so the result is kept rather than redone. The
 * text is stored beside the file, not in SharedPreferences: a book is a
 * megabyte of characters, and preferences are read synchronously on the main
 * thread at startup.
 */
class PdfLibrary(private val context: Context) {

    private val root: File get() = File(context.filesDir, "documents").apply { mkdirs() }

    private val _documents = MutableStateFlow<List<PdfDoc>>(emptyList())
    val documents: StateFlow<List<PdfDoc>> = _documents.asStateFlow()

    init {
        load()
    }

    fun find(id: String): PdfDoc? = _documents.value.firstOrNull { it.id == id }

    /** The imported copy of a document's PDF. */
    fun fileOf(id: String): File = File(dirOf(id), SOURCE_NAME)

    /** Where images pulled out of a document are written. */
    fun imageDirOf(id: String): File = File(dirOf(id), "images")

    private fun dirOf(id: String): File = File(root, id).apply { mkdirs() }

    /**
     * Copies a picked PDF into app storage and returns its new id.
     *
     * Copied rather than referenced because a content URI is borrowed: the
     * permission lapses when the process dies, and a document that stops
     * opening tomorrow is worse than one that took a second to import.
     */
    suspend fun import(uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val name = displayNameOf(uri) ?: "document.pdf"
        val target = fileOf(id)
        try {
            val opened = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            }
            if (opened != true) {
                // Nothing was written, and asking for the target already made
                // the directory. Returning here without this leaves an empty
                // one behind on every revoked permission, for ever.
                runCatching { dirOf(id).deleteRecursively() }
                return@withContext null
            }
            id to name
        } catch (e: Throwable) {
            ErrorLog.report("PDF", "Could not import document", e)
            runCatching { dirOf(id).deleteRecursively() }
            null
        }
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()

    fun put(doc: PdfDoc) {
        _documents.value = _documents.value.filterNot { it.id == doc.id } + doc
        save(doc)
        saveIndex()
    }

    fun remove(id: String) {
        _documents.value = _documents.value.filterNot { it.id == id }
        runCatching { dirOf(id).deleteRecursively() }
        saveIndex()
    }

    // ------------------------------------------------------------ storage

    /**
     * Page text goes in one file separated by form feeds — the character that
     * has meant "page break" since teletypes, and one that cannot appear in
     * extracted prose. Everything else is small enough for JSON.
     */
    private fun save(doc: PdfDoc) {
        runCatching {
            val dir = dirOf(doc.id)
            File(dir, TEXT_NAME).writeText(doc.pages.joinToString(PAGE_SEPARATOR) { it.text })
            File(dir, META_NAME).writeText(
                JSONObject().apply {
                    put("id", doc.id)
                    put("name", doc.name)
                    put("pageCount", doc.pageCount)
                    doc.problem?.let { put("problem", it) }
                    put(
                        "pages",
                        JSONArray().apply {
                            doc.pages.forEach { page ->
                                put(
                                    JSONObject().apply {
                                        put("number", page.number)
                                        put("source", page.source.name)
                                        put("images", page.imageCount)
                                    }
                                )
                            }
                        }
                    )
                }.toString()
            )
        }.onFailure { ErrorLog.report("PDF", "Could not save \"${doc.name}\"", it) }
    }

    private fun saveIndex() {
        runCatching {
            File(root, INDEX_NAME).writeText(
                JSONArray().apply { _documents.value.forEach { put(it.id) } }.toString()
            )
        }.onFailure { ErrorLog.report("PDF", "Could not save the document list", it) }
    }

    private fun load() {
        val index = File(root, INDEX_NAME)
        if (!index.exists()) return
        val loaded = mutableListOf<PdfDoc>()
        runCatching {
            val ids = JSONArray(index.readText())
            for (i in 0 until ids.length()) {
                readDoc(ids.getString(i))?.let { loaded += it }
            }
        }.onFailure {
            ErrorLog.report("PDF", "Could not read the document list", it, Severity.WARNING)
        }
        _documents.value = loaded
    }

    private fun readDoc(id: String): PdfDoc? = runCatching {
        val dir = File(root, id)
        val meta = JSONObject(File(dir, META_NAME).readText())
        val texts = File(dir, TEXT_NAME).takeIf { it.exists() }
            ?.readText()
            ?.split(PAGE_SEPARATOR)
            .orEmpty()

        val pageMeta = meta.optJSONArray("pages") ?: JSONArray()
        val pages = (0 until pageMeta.length()).map { i ->
            val entry = pageMeta.getJSONObject(i)
            PdfPageText(
                number = entry.optInt("number", i + 1),
                text = texts.getOrNull(i).orEmpty(),
                source = TextSource.entries
                    .firstOrNull { it.name == entry.optString("source") }
                    ?: TextSource.NONE,
                imageCount = entry.optInt("images", 0),
            )
        }

        PdfDoc(
            id = meta.optString("id", id),
            name = meta.optString("name", id),
            pageCount = meta.optInt("pageCount", pages.size),
            pages = pages,
            problem = meta.optString("problem").takeIf { it.isNotBlank() },
        )
    }.getOrNull()

    private companion object {
        const val SOURCE_NAME = "source.pdf"
        const val TEXT_NAME = "text.txt"
        const val META_NAME = "meta.json"
        const val INDEX_NAME = "index.json"

        /** U+000C. A page break since teletypes, and impossible in extracted prose. */
        const val PAGE_SEPARATOR = ""
    }
}
