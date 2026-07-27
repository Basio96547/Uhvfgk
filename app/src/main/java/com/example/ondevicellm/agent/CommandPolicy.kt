package com.example.ondevicellm.agent

import com.example.ondevicellm.core.AppStrings

/** How much damage a command can do if it was not what the user meant. */
enum class CommandRisk {
    /** Looks at things. `ls`, `cat`, `ps`, `getprop`. */
    READ_ONLY,

    /** Changes files. `mv`, `rm` inside the sandbox, `>` redirection. */
    WRITES,

    /** Changes the device, or removes a lot at once. */
    DANGEROUS,
}

/**
 * What the terminal will and will not run.
 *
 * The starting point is that the model may need anything — that was the
 * request, and a terminal that only runs an approved list of nine commands is
 * a menu, not a terminal. So this does not maintain an allowlist. It does two
 * narrower things:
 *
 *  - **Refuses a short list outright.** Not on grounds of taste: each of these
 *    either hangs the phone or destroys something with no undo, and there is
 *    no phrasing of "are you sure" that makes a fork bomb a good idea. This
 *    list stays short and every entry earns its place.
 *  - **Classifies everything else**, so the app can ask before a write and
 *    stay out of the way for a read.
 *
 * Worth being plain about the limit that actually matters: this runs as the
 * app's own user in the app sandbox. There is no root. Most of what a
 * dangerous command *would* do is already refused by the kernel — `rm -rf /`
 * gets permission denied on nearly everything. That is the real protection.
 * This is the layer above it, for the parts the sandbox does not cover.
 *
 * Pure, so the classification is tested rather than believed.
 */
object CommandPolicy {

    /**
     * Never run, regardless of approval.
     *
     * A fork bomb needs no privileges and takes the phone down; `mkfs` and a
     * raw `dd` to a block device are unrecoverable if they ever do land; the
     * power commands end the session mid-answer with nothing saved.
     */
    private val NEVER = listOf(
        Regex("""\{\s*:\s*\|\s*:\s*&\s*\}"""),          // :(){ :|:& };:
        Regex("""\bmkfs(\.\w+)?\b"""),
        Regex("""\bdd\b[^\n]*\bof=/dev/(block|mmcblk|sd)"""),
        Regex("""\breboot\b"""),
        Regex("""\bsvc\s+power\b"""),
        Regex("""\bsetprop\s+sys\.powerctl\b"""),
        Regex("""\brm\s+(-[a-zA-Z]*\s+)*/\s*($|;|&)"""), // rm -rf /
        Regex("""\bwipe\s+data\b"""),
    )

    /** Commands that only look. */
    private val READERS = setOf(
        "ls", "cat", "head", "tail", "wc", "grep", "find", "stat", "file", "du", "df",
        "ps", "pwd", "echo", "date", "uname", "id", "whoami", "env", "printenv",
        "getprop", "cksum", "md5sum", "sha1sum", "basename", "dirname", "readlink",
        "sort", "uniq", "cut", "tr", "sed", "awk", "which", "test", "true", "false",
        "cmp", "diff", "od", "xxd", "strings", "top", "free", "uptime", "sleep",
    )

    /** Commands whose whole job is to change the device, not just a file. */
    private val SYSTEM_CHANGERS = setOf(
        "pm", "am", "settings", "setprop", "svc", "cmd", "ime", "content",
        "su", "mount", "umount", "insmod", "rmmod", "iptables",
    )

    /** Non-null when the command is refused. The text says why, in the user's language. */
    fun refusal(command: String, s: AppStrings): String? {
        val text = command.trim()
        if (text.isEmpty()) return s.terminalEmptyCommand
        if (NEVER.any { it.containsMatchIn(text) }) return s.terminalRefused
        return null
    }

    fun classify(command: String): CommandRisk {
        val text = command.trim()
        if (text.isEmpty()) return CommandRisk.READ_ONLY

        // Anything with a redirect writes, whatever the verb in front of it is.
        if (Regex("""(^|[^0-9<>])>>?[^>]""").containsMatchIn(text)) return CommandRisk.WRITES

        var worst = CommandRisk.READ_ONLY
        for (segment in split(text)) {
            val verb = verbOf(segment) ?: continue
            val risk = when {
                verb in SYSTEM_CHANGERS -> CommandRisk.DANGEROUS
                // A recursive or forced delete is a different animal from
                // deleting one named file, even inside the sandbox.
                verb == "rm" && Regex("""\s-\w*[rRf]""").containsMatchIn(segment) ->
                    CommandRisk.DANGEROUS
                verb in READERS -> CommandRisk.READ_ONLY
                else -> CommandRisk.WRITES
            }
            if (risk > worst) worst = risk
        }
        return worst
    }

    /** Splits on the separators a shell treats as "then run this too". */
    internal fun split(command: String): List<String> =
        command.split(Regex("""\|\||&&|[;|&\n]"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** The command word of one segment, ignoring `VAR=x` prefixes and `sudo`. */
    internal fun verbOf(segment: String): String? {
        var words = segment.trim().split(Regex("""\s+"""))
        while (words.isNotEmpty() && (words[0].contains('=') || words[0] == "sudo")) {
            words = words.drop(1)
        }
        return words.firstOrNull()
            ?.substringAfterLast('/')
            ?.trim('(', ')', '"', '\'')
            ?.takeIf { it.isNotEmpty() }
    }
}
