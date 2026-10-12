package io.github.bthal.sammelbecken.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/** Where captures go. Paths are relative to the vault root. */
data class VaultConfig(
    val vaultTreeUri: Uri,
    val notePath: String,
    val audioFolder: String,
)

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var vaultTreeUri: Uri?
        get() = prefs.getString(KEY_VAULT, null)?.let(Uri::parse)
        set(value) = prefs.edit { putString(KEY_VAULT, value?.toString()) }

    var notePath: String
        get() = prefs.getString(KEY_NOTE, "") ?: ""
        set(value) = prefs.edit { putString(KEY_NOTE, value.trim('/', ' ')) }

    var audioFolder: String
        get() = prefs.getString(KEY_AUDIO, "") ?: ""
        set(value) = prefs.edit { putString(KEY_AUDIO, value.trim('/', ' ')) }

    /** Last seen on-screen keyboard height in px, so the capture layout can reserve it before the keyboard appears. */
    var keyboardHeightPx: Int
        get() = prefs.getInt(KEY_KEYBOARD, 0)
        set(value) = prefs.edit { putInt(KEY_KEYBOARD, value) }

    /** Null until the vault and note file are set. An empty audio folder means the vault root. */
    fun config(): VaultConfig? {
        val vault = vaultTreeUri ?: return null
        if (notePath.isBlank()) return null
        return VaultConfig(vault, notePath, audioFolder)
    }

    private companion object {
        const val KEY_VAULT = "vault_tree_uri"
        const val KEY_NOTE = "note_path"
        const val KEY_AUDIO = "audio_folder"
        const val KEY_KEYBOARD = "keyboard_height_px"
    }
}
