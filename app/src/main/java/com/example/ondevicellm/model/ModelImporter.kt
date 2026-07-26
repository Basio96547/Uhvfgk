package com.example.ondevicellm.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Copies a model picked through the Storage Access Framework into app-private
 * storage. Model bundles are large (hundreds of MB to several GB), so the copy
 * reports progress and is cancellable.
 */
class ModelImporter(
    private val context: Context,
    private val registry: ModelRegistry,
) {

    data class Progress(val copiedBytes: Long, val totalBytes: Long) {
        val fraction: Float
            get() = if (totalBytes > 0) (copiedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    /**
     * Streams [uri] into [ModelRegistry.managedDir] and registers it.
     *
     * @throws java.io.IOException if the source cannot be opened, or the device
     *   runs out of space part-way through (the partial file is cleaned up).
     */
    suspend fun import(
        uri: Uri,
        onProgress: (Progress) -> Unit = {},
    ): ModelSpec = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "model.task"
        val totalBytes = sizeOf(uri)

        require(freeSpaceBytes() > totalBytes) {
            "Not enough free storage to import this model " +
                "(needs ${totalBytes / (1024 * 1024)} MB)."
        }

        val target = uniqueTarget(name)
        var copied = 0L

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(Progress(copied, totalBytes))
                    }
                }
            } ?: throw java.io.IOException("Cannot open the selected file.")
        } catch (e: Throwable) {
            target.delete()
            throw e
        }

        val spec = ModelSpec(
            id = UUID.randomUUID().toString(),
            displayName = target.nameWithoutExtension,
            path = target.path,
            kind = guessKindFrom(name),
            supportsThinking = guessThinkingFrom(name),
            managed = true,
            sizeBytes = target.length(),
        )
        registry.add(spec)
        spec
    }

    /**
     * Registers a model that already exists on disk (e.g. `adb push`ed) without
     * copying it.
     */
    fun registerInPlace(path: String, displayName: String? = null): ModelSpec {
        val file = File(path)
        require(file.isFile) { "No file at $path" }
        require(file.canRead()) { "File at $path is not readable by this app." }

        val spec = ModelSpec(
            id = UUID.randomUUID().toString(),
            displayName = displayName ?: file.nameWithoutExtension,
            path = file.path,
            kind = guessKindFrom(file.name),
            supportsThinking = guessThinkingFrom(file.name),
            managed = false,
            sizeBytes = file.length(),
        )
        registry.add(spec)
        return spec
    }

    private fun uniqueTarget(name: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        var candidate = File(registry.managedDir, safe)
        var counter = 1
        while (candidate.exists()) {
            val base = safe.substringBeforeLast('.', safe)
            val ext = safe.substringAfterLast('.', "")
            candidate = File(
                registry.managedDir,
                if (ext.isEmpty()) "${base}_$counter" else "${base}_$counter.$ext",
            )
            counter++
        }
        return candidate
    }

    private fun displayName(uri: Uri): String? {
        DocumentFile.fromSingleUri(context, uri)?.name?.let { return it }
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun sizeOf(uri: Uri): Long {
        DocumentFile.fromSingleUri(context, uri)?.length()?.takeIf { it > 0 }?.let { return it }
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else 0L
        } ?: 0L
    }

    private fun freeSpaceBytes(): Long = registry.managedDir.usableSpace

    private companion object {
        fun guessKindFrom(name: String): ModelKind {
            val n = name.lowercase()
            return when {
                listOf("whisper", "asr", "speech", "wav2vec", "moonshine")
                    .any { n.contains(it) } -> ModelKind.AUDIO

                listOf("3n", "vision", "vl", "omni", "multimodal")
                    .any { n.contains(it) } -> ModelKind.MULTIMODAL

                else -> ModelKind.TEXT
            }
        }

        fun guessThinkingFrom(name: String): Boolean {
            val n = name.lowercase()
            return listOf("qwen3", "r1", "deepseek", "think", "reason", "cot")
                .any { n.contains(it) }
        }
    }
}
