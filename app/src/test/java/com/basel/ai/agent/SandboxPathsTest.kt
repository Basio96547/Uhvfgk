package com.basel.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SandboxPathsTest {

    private val root = "/data/user/0/app/files/workspace"

    @Test
    fun `a relative path lands inside the workspace`() {
        assertEquals("$root/notes.txt", SandboxPaths.resolve(root, "notes.txt"))
        assertEquals("$root/a/b.txt", SandboxPaths.resolve(root, "a/b.txt"))
        assertEquals("$root/a/b.txt", SandboxPaths.resolve(root, "./a/./b.txt"))
    }

    @Test
    fun `traversal is refused`() {
        // The case this exists for: "save these notes" writing over the model
        // registry two directories up.
        assertNull(SandboxPaths.resolve(root, "../../registry.json"))
        assertNull(SandboxPaths.resolve(root, "a/../../../etc/passwd"))
        assertNull(SandboxPaths.resolve(root, "/etc/passwd"))
    }

    @Test
    fun `an absolute path already inside the root is allowed`() {
        // A model that helpfully expands the path itself should not be punished.
        assertEquals("$root/a.txt", SandboxPaths.resolve(root, "$root/a.txt"))
    }

    @Test
    fun `climbing back down to the root is allowed`() {
        assertEquals("$root/b", SandboxPaths.resolve(root, "a/../b"))
        assertEquals(root, SandboxPaths.resolve(root, "."))
    }

    @Test
    fun `a sibling directory with the same prefix is still outside`() {
        // "/…/workspace-backup" starts with "/…/workspace" as a string and is
        // not inside it.
        assertNull(SandboxPaths.resolve(root, "../workspace-backup/x"))
    }

    @Test
    fun `normalize collapses without touching the disk`() {
        assertEquals("/a/b", SandboxPaths.normalize("/a//b/"))
        assertEquals("/a", SandboxPaths.normalize("/a/b/.."))
        assertEquals("/", SandboxPaths.normalize("/a/../.."))
        assertEquals("a/b", SandboxPaths.normalize("./a/b"))
    }

    @Test
    fun `display is relative to the workspace`() {
        assertEquals("a/b.txt", SandboxPaths.display(root, "$root/a/b.txt"))
        assertEquals(".", SandboxPaths.display(root, root))
        assertEquals("/etc/hosts", SandboxPaths.display(root, "/etc/hosts"))
    }
}
