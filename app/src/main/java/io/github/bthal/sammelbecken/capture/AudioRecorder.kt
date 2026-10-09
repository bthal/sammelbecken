package io.github.bthal.sammelbecken.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.File

/** Records AAC/.m4a into the app cache. A recording only reaches the vault if the user presses Send. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording get() = recorder != null

    val elapsedMs get() = if (isRecording) SystemClock.elapsedRealtime() - startedAt else 0L

    fun start() {
        check(recorder == null)
        val out = File(context.cacheDir, "capture.m4a").also { it.delete() }
        recorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(64_000)
            setOutputFile(out)
            prepare()
            start()
        }
        file = out
        startedAt = SystemClock.elapsedRealtime()
    }

    /** Input level 0..1 since the last call. */
    fun level(): Float = (recorder?.maxAmplitude ?: 0) / 32767f

    /** Stops and returns the recording, or null if it was shorter than [minMs] or failed. */
    fun stop(minMs: Long): File? {
        val duration = elapsedMs
        val ok = release()
        val out = file ?: return null
        file = null
        if (ok && duration >= minMs && out.length() > 0) return out
        out.delete()
        return null
    }

    /** Stops and throws the recording away. */
    fun cancel() {
        release()
        file?.delete()
        file = null
    }

    private fun release(): Boolean {
        val r = recorder ?: return false
        recorder = null
        // stop() throws if no valid audio was captured yet (e.g. stopped immediately).
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        return ok
    }
}
