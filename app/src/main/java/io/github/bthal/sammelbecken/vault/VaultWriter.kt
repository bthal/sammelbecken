package io.github.bthal.sammelbecken.vault

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.bthal.sammelbecken.settings.VaultConfig
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/** Writes captures into the vault through the Storage Access Framework. Call off the main thread. */
class VaultWriter(context: Context, private val config: VaultConfig) {

    private val resolver = context.contentResolver
    private val root: DocumentFile = DocumentFile.fromTreeUri(context, config.vaultTreeUri)
        ?: throw IOException("Vault folder is not accessible")

    fun appendText(text: String) = appendEntry(Entries.textEntry(text))

    /** Copies [recording] into the audio folder and appends an embed for it to the note. */
    fun appendAudio(recording: File, fileName: String) {
        val folder = resolveDirectory(VaultPaths.segments(config.audioFolder))
        // octet-stream keeps the display name exactly as given (no extension rewriting by the provider).
        val doc = folder.createFile("application/octet-stream", fileName)
            ?: throw IOException("Could not create $fileName")
        resolver.openOutputStream(doc.uri, "w").use { out ->
            requireNotNull(out) { "Could not open $fileName" }
            recording.inputStream().use { it.copyTo(out) }
        }
        // The provider may have renamed it on a name clash, e.g. "x (1).m4a".
        val actualName = doc.name ?: fileName
        appendEntry(Entries.audioEntry(VaultPaths.join(config.audioFolder, actualName)))
    }

    private fun appendEntry(entry: String) {
        val note = resolveNote()
        val chunk = Entries.appendChunk(entry, lastChar(note.uri)).toByteArray()
        try {
            resolver.openOutputStream(note.uri, "wa").use { out ->
                requireNotNull(out).write(chunk)
            }
        } catch (e: Exception) {
            // Some providers do not support append mode; fall back to rewriting the whole file.
            val existing = resolver.openInputStream(note.uri)?.use { it.readBytes() } ?: ByteArray(0)
            resolver.openOutputStream(note.uri, "wt").use { out ->
                requireNotNull(out).write(existing + chunk)
            }
        }
    }

    private fun lastChar(uri: Uri): Char? =
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { input ->
                val channel = input.channel
                val size = channel.size()
                if (size == 0L) return@use null
                channel.position(size - 1)
                input.read().toChar()
            }
        }

    private fun resolveNote(): DocumentFile {
        val segments = VaultPaths.segments(config.notePath)
        require(segments.isNotEmpty()) { "No note file configured" }
        val dir = resolveDirectory(segments.dropLast(1))
        val name = segments.last()
        return dir.findFile(name)
            ?: dir.createFile("application/octet-stream", name)
            ?: throw IOException("Could not create note $name")
    }

    /** Walks (and creates where missing) the given folder path below the vault root. */
    private fun resolveDirectory(segments: List<String>): DocumentFile =
        segments.fold(root) { dir, name ->
            dir.findFile(name)?.takeIf { it.isDirectory }
                ?: dir.createDirectory(name)
                ?: throw IOException("Could not create folder $name")
        }
}
