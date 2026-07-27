package com.example.ondevicellm.agent

/**
 * Keeps writes inside the workspace.
 *
 * Reading is left alone — the OS already decides what this app's user can
 * read, and a model that wants to look at `/proc/cpuinfo` should be able to.
 * Writing is different: `../../` in a path the model composed is how a
 * "save these notes" turn quietly overwrites the model registry.
 *
 * Pure string arithmetic, so the traversal cases are tested rather than
 * assumed. `File.getCanonicalPath` would also work on the device and cannot be
 * tested here at all.
 */
object SandboxPaths {

    /**
     * [path] resolved against [root], or null when it escapes.
     *
     * Absolute paths are allowed only when they are already inside the root,
     * so a model that helpfully expands a path itself is not punished for it.
     */
    fun resolve(root: String, path: String): String? {
        val cleanRoot = normalize(root).trimEnd('/')
        if (cleanRoot.isEmpty()) return null

        val raw = path.trim()
        if (raw.isEmpty()) return cleanRoot

        val combined = if (raw.startsWith("/")) raw else "$cleanRoot/$raw"
        val resolved = normalize(combined)

        return if (resolved == cleanRoot || resolved.startsWith("$cleanRoot/")) resolved else null
    }

    /** Collapses `.`, `..` and repeated slashes without touching the disk. */
    fun normalize(path: String): String {
        val absolute = path.startsWith("/")
        val parts = mutableListOf<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty() && parts.last() != "..") {
                    parts.removeAt(parts.size - 1)
                } else if (!absolute) {
                    // A relative path may legitimately start above itself; an
                    // absolute one cannot go above the root.
                    parts += ".."
                }
                else -> parts += segment
            }
        }
        val joined = parts.joinToString("/")
        return if (absolute) "/$joined" else joined
    }

    /** The path as the model should see it: relative to the workspace. */
    fun display(root: String, path: String): String {
        val cleanRoot = normalize(root).trimEnd('/')
        val clean = normalize(path)
        return when {
            clean == cleanRoot -> "."
            clean.startsWith("$cleanRoot/") -> clean.removePrefix("$cleanRoot/")
            else -> clean
        }
    }
}
