package com.wispr.client

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.media.MediaRecorder
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.wispr.client.overlay.AccessibilityPermission
import com.wispr.client.overlay.OverlayConfigStore
import com.wispr.client.overlay.OverlayPermission
import com.wispr.client.overlay.WhisperFloatingBubbleService
import com.wispr.client.overlay.WhisperFocusAccessibilityService
import com.wispr.client.data.ServerConfigStore
import com.wispr.client.data.TranscriptStore
import com.wispr.client.network.WhisperServerClient
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serverConfigStore = ServerConfigStore(this)
        val transcriptStore = TranscriptStore(this)
        val overlayConfigStore = OverlayConfigStore(this)
        val serverClient = WhisperServerClient()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        serverConfigStore = serverConfigStore,
                        transcriptStore = transcriptStore,
                        overlayConfigStore = overlayConfigStore,
                        serverClient = serverClient,
                        onOpenOverlaySettings = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"),
                            )
                            startActivity(intent)
                        },
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onStartBubbleService = {
                            WhisperFloatingBubbleService.sendCommand(
                                this,
                                WhisperFloatingBubbleService.ACTION_START,
                            )
                        },
                        onStopBubbleService = {
                            WhisperFloatingBubbleService.sendCommand(
                                this,
                                WhisperFloatingBubbleService.ACTION_STOP,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    serverConfigStore: ServerConfigStore,
    transcriptStore: TranscriptStore,
    overlayConfigStore: OverlayConfigStore,
    serverClient: WhisperServerClient,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartBubbleService: () -> Unit,
    onStopBubbleService: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf("") }
    var allowInsecureHttps by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Idle") }
    var transcript by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var canDrawOverlay by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var showBubbleWithoutKeyboard by remember { mutableStateOf(false) }

    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var currentAudioFile by remember { mutableStateOf<File?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            statusText = "Microphone permission denied"
            return@rememberLauncherForActivityResult
        }

        try {
            val outputFile = File(context.cacheDir, "recording-${System.currentTimeMillis()}.webm")
            val recorder = MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.WEBM)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            currentAudioFile = outputFile
            isRecording = true
            statusText = "Recording... tap Stop to transcribe"
        } catch (e: Exception) {
            statusText = "Recording failed: ${e.message ?: "unknown error"}"
        }
    }

    LaunchedEffect(Unit) {
        baseUrl = serverConfigStore.getBaseUrl()
        allowInsecureHttps = serverConfigStore.getAllowInsecureHttps()
        transcript = transcriptStore.getLastTranscript()
        canDrawOverlay = OverlayPermission.canDraw(context)
        accessibilityEnabled = AccessibilityPermission.isServiceEnabled(
            context,
            WhisperFocusAccessibilityService::class.java,
        )
        showBubbleWithoutKeyboard = overlayConfigStore.getShowBubbleWithoutKeyboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "WhisperClient", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            label = { Text("Server base URL") },
            singleLine = true,
        )

        RowLine(
            label = "Allow insecure HTTPS (self-signed cert)",
            control = {
                Switch(
                    checked = allowInsecureHttps,
                    onCheckedChange = { allowInsecureHttps = it },
                )
            },
        )

        Button(
            onClick = {
                serverConfigStore.setBaseUrl(baseUrl)
                serverConfigStore.setAllowInsecureHttps(allowInsecureHttps)
                statusText = "Saved config"
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Save Config")
        }

        Button(
            onClick = {
                scope.launch {
                    statusText = "Checking server..."
                    val result = serverClient.healthCheck(baseUrl, allowInsecureHttps)
                    statusText = result.fold(
                        onSuccess = { "Server reachable (HTTP $it)" },
                        onFailure = { "Server check failed: ${it.message ?: "unknown error"}" },
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Check Server")
        }

        Button(
            onClick = {
                if (!isRecording) {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@Button
                }

                val recorder = mediaRecorder
                val audioFile = currentAudioFile
                if (recorder == null || audioFile == null) {
                    statusText = "No active recording"
                    isRecording = false
                    return@Button
                }

                try {
                    recorder.stop()
                } catch (_: Exception) {
                }
                recorder.release()

                mediaRecorder = null
                currentAudioFile = null
                isRecording = false

                scope.launch {
                    statusText = "Transcribing..."
                    val result = serverClient.transcribeAudio(baseUrl, audioFile, allowInsecureHttps)
                    statusText = result.fold(
                        onSuccess = { text ->
                            transcript = text
                            transcriptStore.setLastTranscript(text)
                            "Transcription ready"
                        },
                        onFailure = { "Transcription failed: ${it.message ?: "unknown error"}" },
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(if (isRecording) "Stop + Transcribe" else "Start Recording")
        }

        Button(
            onClick = {
                if (transcript.isNotBlank()) {
                    clipboard.setText(AnnotatedString(transcript))
                    statusText = "Copied transcript"
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Copy Transcript")
        }

        OutlinedTextField(
            value = transcript,
            onValueChange = {
                transcript = it
                transcriptStore.setLastTranscript(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            label = { Text("Last Transcript") },
            minLines = 4,
        )

        Text(
            text = statusText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )

        Text(
            text = "Flow Bubble (Overlay + Accessibility)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        )

        Text(
            text = "Overlay: ${if (canDrawOverlay) "granted" else "not granted"} | Accessibility: ${if (accessibilityEnabled) "enabled" else "disabled"}",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        RowLine(
            label = "Show bubble without keyboard",
            control = {
                Switch(
                    checked = showBubbleWithoutKeyboard,
                    onCheckedChange = { value ->
                        showBubbleWithoutKeyboard = value
                        overlayConfigStore.setShowBubbleWithoutKeyboard(value)
                    },
                )
            },
        )

        Button(
            onClick = onOpenOverlaySettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Open Overlay Permission")
        }

        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Open Accessibility Settings")
        }

        Button(
            onClick = {
                canDrawOverlay = OverlayPermission.canDraw(context)
                accessibilityEnabled = AccessibilityPermission.isServiceEnabled(
                    context,
                    WhisperFocusAccessibilityService::class.java,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Refresh Bubble Status")
        }

        Button(
            onClick = onStartBubbleService,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Start Bubble Service")
        }

        Button(
            onClick = onStopBubbleService,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Stop Bubble Service")
        }
    }
}

@Composable
private fun RowLine(label: String, control: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        control()
    }
}
