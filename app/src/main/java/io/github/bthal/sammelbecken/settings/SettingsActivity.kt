package io.github.bthal.sammelbecken.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.bthal.sammelbecken.capture.CaptureActivity
import io.github.bthal.sammelbecken.theme.SammelbeckenTheme
import io.github.bthal.sammelbecken.vault.VaultPaths

/** Launcher entry: choose the vault, the target note and the audio folder. */
class SettingsActivity : ComponentActivity() {

    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = SettingsStore(this)
        setContent {
            SammelbeckenTheme {
                Surface(Modifier.fillMaxSize()) { SettingsScreen() }
            }
        }
    }

    @Composable
    private fun SettingsScreen() {
        var vault by remember { mutableStateOf(store.vaultTreeUri) }
        var notePath by remember { mutableStateOf(store.notePath) }
        var audioFolder by remember { mutableStateOf(store.audioFolder) }
        var error by remember { mutableStateOf<String?>(null) }
        var micGranted by remember { mutableStateOf(hasMic()) }

        LifecycleResumeEffect(Unit) {
            micGranted = hasMic()
            onPauseOrDispose { }
        }

        val pickVault = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            store.vaultTreeUri?.takeIf { it != uri }?.let { old ->
                runCatching { contentResolver.releasePersistableUriPermission(old, flags) }
            }
            contentResolver.takePersistableUriPermission(uri, flags)
            store.vaultTreeUri = uri
            vault = uri
            error = null
        }

        val pickNote = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val rel = uri?.let { relativeToVault(it, DocumentsContract.getDocumentId(it)) }
            if (uri == null) return@rememberLauncherForActivityResult
            if (rel.isNullOrEmpty()) {
                error = "The note must be inside the vault folder."
            } else {
                store.notePath = rel; notePath = rel; error = null
            }
        }

        val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val rel = relativeToVault(uri, DocumentsContract.getTreeDocumentId(uri))
            if (rel == null) {
                error = "The audio folder must be inside the vault folder."
            } else {
                store.audioFolder = rel; audioFolder = rel; error = null
            }
        }

        val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            micGranted = it
        }

        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Sammelbecken", style = MaterialTheme.typography.headlineMedium)

            Section("1. Obsidian vault") {
                Text(vault?.let { DocumentsContract.getTreeDocumentId(it) } ?: "Not set")
                Button(onClick = { pickVault.launch(vault) }) { Text("Choose vault folder") }
            }

            Section("2. Note file (captures are appended here)") {
                PathRow(
                    value = notePath,
                    placeholder = "Inbox/Sammelbecken.md",
                    enabled = vault != null,
                    onValueChange = { notePath = it; store.notePath = it },
                    onBrowse = { pickNote.launch(arrayOf("*/*")) },
                )
                Text("Created if it doesn't exist.", style = MaterialTheme.typography.bodySmall)
            }

            Section("3. Audio folder") {
                PathRow(
                    value = audioFolder,
                    placeholder = "Attachments/Audio (empty = vault root)",
                    enabled = vault != null,
                    onValueChange = { audioFolder = it; store.audioFolder = it },
                    onBrowse = { pickAudio.launch(vault) },
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Section("4. Microphone") {
                if (micGranted) {
                    Text("Granted")
                } else {
                    Button(onClick = { requestMic.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Grant microphone") }
                }
            }

            Section("5. Assistant gesture") {
                Text("Set Sammelbecken as \"Digital assistant app\" under Default apps.")
                Button(onClick = { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }) {
                    Text("Open default apps")
                }
            }

            OutlinedButton(
                onClick = { startActivity(Intent(this@SettingsActivity, CaptureActivity::class.java)) },
                enabled = vault != null && notePath.isNotBlank(),
            ) { Text("Try a capture") }
        }
    }

    @Composable
    private fun Section(title: String, content: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }

    @Composable
    private fun PathRow(
        value: String,
        placeholder: String,
        enabled: Boolean,
        onValueChange: (String) -> Unit,
        onBrowse: () -> Unit,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onBrowse, enabled = enabled) { Text("Browse") }
        }
    }

    /** Vault-relative path for a picked document, or null if it isn't inside the vault. */
    private fun relativeToVault(picked: Uri, docId: String): String? {
        val vault = store.vaultTreeUri ?: return null
        if (picked.authority != vault.authority) return null
        return VaultPaths.relativePath(DocumentsContract.getTreeDocumentId(vault), docId)
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
