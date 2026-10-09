package io.github.bthal.sammelbecken.capture

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.bthal.sammelbecken.settings.SettingsActivity
import io.github.bthal.sammelbecken.theme.SammelbeckenTheme

/**
 * The capture screen, opened by the assistant gesture (also over the lock screen).
 * Recording starts immediately; typing switches to a text capture; Send saves; leaving discards.
 */
class CaptureActivity : ComponentActivity() {

    private val vm: CaptureViewModel by viewModels()

    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()

        if (savedInstanceState == null && vm.configured) {
            if (hasMic()) vm.startRecording() else requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            SammelbeckenTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (vm.configured) CaptureScreen() else NotConfigured()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Back, home, screen off or swiping away without Send: throw it all away.
        if (!isChangingConfigurations) {
            vm.discard()
            finish()
        }
    }

    private fun send() {
        vm.send { saved ->
            if (saved) window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            finish()
        }
    }

    @Composable
    private fun CaptureScreen() {
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            focus.requestFocus()
            keyboard?.show()
        }

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusStrip()
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vm.text,
                    onValueChange = vm::onTextChange,
                    placeholder = { Text("Type, or just speak…") },
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .focusRequester(focus),
                )
                Button(
                    onClick = ::send,
                    enabled = !vm.saving,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    if (vm.saving) CircularProgressIndicator(Modifier.size(32.dp))
                    else Text("Send", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    private fun StatusStrip() {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (vm.mic) {
                    MicState.Recording -> {
                        RecordingDot()
                        Text(formatElapsed(vm.elapsedMs), style = MaterialTheme.typography.labelLarge)
                        LinearProgressIndicator(
                            progress = { vm.level.coerceIn(0f, 1f) },
                            modifier = Modifier.width(120.dp),
                            drawStopIndicator = {},
                        )
                    }
                    MicState.Stopped -> Text("Text note", style = MaterialTheme.typography.labelLarge)
                    MicState.Off -> Text("Microphone off", style = MaterialTheme.typography.labelLarge)
                }
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    @Composable
    private fun RecordingDot() {
        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "alpha",
        )
        Box(
            Modifier
                .size(12.dp)
                .alpha(pulse)
                .background(Color(0xFFE53935), CircleShape)
        )
    }

    @Composable
    private fun NotConfigured() {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Choose your Obsidian vault and note first.")
            Button(onClick = ::openSettings) { Text("Open settings") }
        }
    }

    private fun openSettings() {
        val launch = {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launch()
            })
        } else {
            launch()
        }
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun formatElapsed(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
