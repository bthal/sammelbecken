package io.github.bthal.sammelbecken.vault

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Pure helpers for what gets written into the vault. No Android dependencies, so they are unit-testable. */
object Entries {

    private val audioNameFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

    /** `- [ ] text`; continuation lines are indented so multi-line text stays inside the checklist item. */
    fun textEntry(text: String): String {
        val lines = text.trim().lines().map { it.trimEnd() }
        return "- [ ] " + lines.joinToString("\n  ")
    }

    /** `- [ ] ![[Attachments/Audio/2026-10-09_143201.m4a]]` — Obsidian renders this as an inline audio player. */
    fun audioEntry(vaultRelativePath: String): String = "- [ ] ![[$vaultRelativePath]]"

    fun audioFileName(time: LocalDateTime): String = audioNameFormat.format(time) + ".m4a"

    /**
     * Text to append to a file so that [entry] ends up on its own line.
     * [lastChar] is the file's current last character, or null if the file is empty.
     */
    fun appendChunk(entry: String, lastChar: Char?): String {
        val prefix = if (lastChar == null || lastChar == '\n') "" else "\n"
        return prefix + entry + "\n"
    }
}

/** Path math on Storage Access Framework document IDs (e.g. `primary:Documents/Vault/Inbox.md`). */
object VaultPaths {

    /** Path of [docId] relative to the vault root [treeDocId], or null if it lies outside the vault. */
    fun relativePath(treeDocId: String, docId: String): String? {
        if (docId == treeDocId) return ""
        val prefix = if (treeDocId.endsWith(":") || treeDocId.endsWith("/")) treeDocId else "$treeDocId/"
        return if (docId.startsWith(prefix)) docId.removePrefix(prefix).trim('/') else null
    }

    /** Joins vault-relative path segments, ignoring empty ones. */
    fun join(vararg parts: String): String =
        parts.flatMap { it.split('/') }.filter { it.isNotBlank() }.joinToString("/")

    fun segments(relativePath: String): List<String> = relativePath.split('/').filter { it.isNotBlank() }
}
