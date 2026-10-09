package io.github.bthal.sammelbecken.assist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import io.github.bthal.sammelbecken.capture.CaptureActivity

/*
 * Minimal voice-interaction plumbing so Android lists Sammelbecken under
 * Settings → Default apps → Digital assistant app. The assistant gesture then
 * opens a session, which immediately hands over to CaptureActivity.
 */

class SammelVoiceInteractionService : VoiceInteractionService()

class SammelSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = SammelSession(this)
}

class SammelSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        startAssistantActivity(
            Intent(context, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        hide()
    }
}

/** Required by the platform for a voice-interaction service; Sammelbecken does no recognition. */
class SammelRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
    }
    override fun onCancel(listener: Callback?) = Unit
    override fun onStopListening(listener: Callback?) = Unit
}
