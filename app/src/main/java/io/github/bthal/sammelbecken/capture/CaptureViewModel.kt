package io.github.bthal.sammelbecken.capture

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.bthal.sammelbecken.settings.SettingsStore
import io.github.bthal.sammelbecken.vault.Entries
import io.github.bthal.sammelbecken.vault.VaultWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

enum class MicState { Off, Recording, Stopped }

class CaptureViewModel(app: Application) : AndroidViewModel(app) {

    private val recorder = AudioRecorder(app)
    private val settings = SettingsStore(app)
    private var meterJob: Job? = null

    var text by mutableStateOf("")
        private set
    var mic by mutableStateOf(MicState.Off)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Recording that was stopped by Send but couldn't be written yet (kept for a retry). */
    private var pendingAudio: File? = null

    val configured get() = settings.config() != null

    fun startRecording() {
        if (mic != MicState.Off || text.isNotEmpty()) return
        try {
            recorder.start()
        } catch (e: Exception) {
            error = "Microphone unavailable"
            return
        }
        mic = MicState.Recording
        meterJob = viewModelScope.launch {
            while (isActive) {
                elapsedMs = recorder.elapsedMs
                level = recorder.level()
                delay(100)
            }
        }
    }

    fun onTextChange(value: String) {
        // Typing turns this into a text capture: drop the audio.
        if (value.isNotEmpty() && mic == MicState.Recording) {
            stopMeter()
            recorder.cancel()
            mic = MicState.Stopped
        }
        if (value.isNotEmpty()) pendingAudio?.delete().also { pendingAudio = null }
        text = value
    }

    /** Saves the capture. [onDone] is called with true if something was written, false if there was nothing to save. */
    fun send(onDone: (saved: Boolean) -> Unit) {
        if (saving) return
        val config = settings.config() ?: run { error = "Set up Sammelbecken first"; return }
        val note = text.takeIf { it.isNotBlank() }
        val audio = pendingAudio ?: if (note == null && mic == MicState.Recording) {
            stopMeter()
            mic = MicState.Stopped
            recorder.stop(minMs = MIN_AUDIO_MS)
        } else null
        if (note == null && audio == null) {
            onDone(false)
            return
        }
        saving = true
        error = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val writer = VaultWriter(getApplication(), config)
                    if (note != null) writer.appendText(note)
                    else writer.appendAudio(audio!!, Entries.audioFileName(LocalDateTime.now()))
                }
            }
            saving = false
            result.onSuccess {
                audio?.delete()
                pendingAudio = null
                onDone(true)
            }.onFailure {
                pendingAudio = audio
                error = "Couldn't save: ${it.message}"
            }
        }
    }

    /** Leaving without Send throws everything away. */
    fun discard() {
        stopMeter()
        recorder.cancel()
        pendingAudio?.delete()
        pendingAudio = null
        mic = MicState.Off
        text = ""
    }

    private fun stopMeter() {
        meterJob?.cancel()
        meterJob = null
        level = 0f
    }

    override fun onCleared() = discard()

    private companion object {
        const val MIN_AUDIO_MS = 1_000L
    }
}
