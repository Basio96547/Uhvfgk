package com.example.ondevicellm.agent

import com.example.ondevicellm.core.ArabicStrings
import com.example.ondevicellm.core.EnglishStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPolicyTest {

    // ------------------------------------------------------------- refusals

    @Test
    fun `the never list is refused`() {
        val never = listOf(
            ":(){ :|:& };:",
            "mkfs.ext4 /dev/block/sda",
            "dd if=/dev/zero of=/dev/block/mmcblk0",
            "reboot",
            "svc power reboot",
            "rm -rf /",
        )
        for (command in never) {
            assertNotNull(command, CommandPolicy.refusal(command, EnglishStrings))
        }
    }

    @Test
    fun `ordinary commands are not refused`() {
        val fine = listOf(
            "ls -la",
            "cat /proc/cpuinfo",
            "rm -rf build/",           // destructive, but inside the sandbox — classified, not refused
            "echo hello > note.txt",
            "find . -name '*.gguf'",
            "getprop ro.product.model",
        )
        for (command in fine) {
            assertNull(command, CommandPolicy.refusal(command, EnglishStrings))
        }
    }

    @Test
    fun `an empty command is refused with its own reason`() {
        assertEquals(EnglishStrings.terminalEmptyCommand, CommandPolicy.refusal("   ", EnglishStrings))
    }

    @Test
    fun `refusals speak the reader's language`() {
        val arabic = CommandPolicy.refusal("reboot", ArabicStrings)
        assertNotNull(arabic)
        assertTrue(arabic!!.any { it in '؀'..'ۿ' })
    }

    // ------------------------------------------------------ classification

    @Test
    fun `reading is read-only`() {
        for (command in listOf("ls", "cat a.txt", "ps -A", "grep x f", "df -h", "getprop")) {
            assertEquals(command, CommandRisk.READ_ONLY, CommandPolicy.classify(command))
        }
    }

    @Test
    fun `a redirect is a write whatever the verb was`() {
        // `echo` alone reads; `echo > file` does not.
        assertEquals(CommandRisk.WRITES, CommandPolicy.classify("echo hi > note.txt"))
        assertEquals(CommandRisk.WRITES, CommandPolicy.classify("cat a >> b"))
    }

    @Test
    fun `a pipe is not a redirect`() {
        assertEquals(CommandRisk.READ_ONLY, CommandPolicy.classify("ps -A | grep llm"))
        assertEquals(CommandRisk.READ_ONLY, CommandPolicy.classify("ls -la | head -5"))
    }

    @Test
    fun `unknown commands are assumed to write`() {
        // The safe assumption for something the list has never seen.
        assertEquals(CommandRisk.WRITES, CommandPolicy.classify("mkdir out"))
        assertEquals(CommandRisk.WRITES, CommandPolicy.classify("touch a"))
    }

    @Test
    fun `a recursive delete is dangerous even in the sandbox`() {
        assertEquals(CommandRisk.DANGEROUS, CommandPolicy.classify("rm -rf work"))
        assertEquals(CommandRisk.DANGEROUS, CommandPolicy.classify("rm -f a.txt"))
        // Deleting one named file is an ordinary write.
        assertEquals(CommandRisk.WRITES, CommandPolicy.classify("rm a.txt"))
    }

    @Test
    fun `system commands are dangerous`() {
        for (command in listOf("pm uninstall x", "settings put global x 1", "su", "mount -o rw /")) {
            assertEquals(command, CommandRisk.DANGEROUS, CommandPolicy.classify(command))
        }
    }

    @Test
    fun `the worst segment decides`() {
        // A read chained to a system change is not a read.
        assertEquals(CommandRisk.DANGEROUS, CommandPolicy.classify("ls && pm uninstall x"))
        assertEquals(CommandRisk.WRITES, CommandPolicy.classify("ls; mkdir out"))
    }

    // --------------------------------------------------------------- verbs

    @Test
    fun `the verb survives paths, env prefixes and sudo`() {
        assertEquals("ls", CommandPolicy.verbOf("/system/bin/ls -la"))
        assertEquals("ls", CommandPolicy.verbOf("LANG=C ls"))
        assertEquals("ls", CommandPolicy.verbOf("sudo ls"))
        assertNull(CommandPolicy.verbOf("   "))
    }

    @Test
    fun `splitting finds every segment a shell would run`() {
        assertEquals(listOf("a", "b", "c"), CommandPolicy.split("a && b ; c"))
        assertEquals(listOf("a", "b"), CommandPolicy.split("a || b"))
        assertEquals(listOf("ps", "grep x"), CommandPolicy.split("ps | grep x"))
    }
}
