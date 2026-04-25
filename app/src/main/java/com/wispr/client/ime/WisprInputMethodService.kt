package com.wispr.client.ime

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wispr.client.R
import com.wispr.client.data.ServerConfigStore
import com.wispr.client.data.TranscriptStore
import com.wispr.client.network.WhisperServerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class WhisperInputMethodService : InputMethodService() {
    private val imeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var transcriptStore: TranscriptStore
    private lateinit var serverConfigStore: ServerConfigStore
    private val serverClient = WhisperServerClient()

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var isRecording = false
    private var recordingStartedAt: Long = 0L

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.ime_view, null)
        transcriptStore = TranscriptStore(this)
        serverConfigStore = ServerConfigStore(this)

        val recordButton = view.findViewById<Button>(R.id.recordButton)
        val insertButton = view.findViewById<Button>(R.id.insertButton)
        val copyButton = view.findViewById<Button>(R.id.copyButton)
        val statusText = view.findViewById<TextView>(R.id.statusText)

        insertButton.setOnClickListener {
            val text = transcriptStore.getLastTranscript().ifBlank { "No transcript yet" }
            val outcome = TranscriptDelivery.deliver(
                text = text,
                textInserter = currentInputConnection?.let { input ->
                    TextInserter { value -> input.commitText(value, 1) }
                },
                clipboardWriter = ClipboardWriter { value ->
                    copyToClipboard(value)
                },
            )
            statusText.text = outcome.statusLabel
            Log.i(TAG, "Insert action outcome: $outcome")
        }

        copyButton.setOnClickListener {
            val text = transcriptStore.getLastTranscript().ifBlank { "No transcript yet" }
            copyToClipboard(text)
            statusText.text = "Copied transcript"
            Log.i(TAG, "Copied transcript to clipboard")
        }

        recordButton.setOnClickListener {
            if (!isRecording) {
                startRecording(recordButton, statusText)
            } else {
                stopAndTranscribe(recordButton, statusText)
            }
        }

        statusText.text = "Idle"
        return view
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        releaseRecorder()
    }

    override fun onDestroy() {
        releaseRecorder()
        imeScope.cancel()
        super.onDestroy()
    }

    private fun startRecording(recordButton: Button, statusText: TextView) {
        if (!hasRecordAudioPermission()) {
            statusText.text = "Mic permission missing: open app once"
            Toast.makeText(this, "Grant microphone permission in WhisperClient app", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val outputFile = File(cacheDir, "ime-recording-${System.currentTimeMillis()}.webm")
            val recorder = MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.WEBM)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()

            recordingStartedAt = System.currentTimeMillis()
            mediaRecorder = recorder
            currentAudioFile = outputFile
            isRecording = true
            recordButton.text = "Stop"
            statusText.text = "Recording..."
            Log.i(TAG, "Started IME recording")
        } catch (e: Exception) {
            releaseRecorder()
            statusText.text = "Recording failed: ${e.message ?: "unknown"}"
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    private fun stopAndTranscribe(recordButton: Button, statusText: TextView) {
        val recorder = mediaRecorder
        val audioFile = currentAudioFile
        if (recorder == null || audioFile == null) {
            releaseRecorder()
            statusText.text = "No active recording"
            recordButton.text = "Record"
            return
        }

        val durationMs = if (recordingStartedAt > 0) System.currentTimeMillis() - recordingStartedAt else 0L
        recordingStartedAt = 0L
        try {
            recorder.stop()
        } catch (_: Exception) {
        }
        releaseRecorder()
        recordButton.text = "Record"
        statusText.text = "Transcribing..."

        val baseUrl = serverConfigStore.getBaseUrl()
        val allowInsecureHttps = serverConfigStore.getAllowInsecureHttps()
        imeScope.launch {
            val result = serverClient.transcribeAudio(baseUrl, audioFile, allowInsecureHttps)
            if (!audioFile.delete()) {
                Log.w(TAG, "Failed to delete temporary recording: ${audioFile.absolutePath}")
            }

            result.fold(
                onSuccess = { text ->
                    com.wispr.client.data.SessionRecorder.record(
                        this@WhisperInputMethodService,
                        transcript = text,
                        durationMs = durationMs,
                        sourceApp = null,
                    )
                    val outcome = TranscriptDelivery.deliver(
                        text = text,
                        textInserter = currentInputConnection?.let { input ->
                            TextInserter { value -> input.commitText(value, 1) }
                        },
                        clipboardWriter = ClipboardWriter { value ->
                            copyToClipboard(value)
                        },
                    )
                    statusText.text = when (outcome) {
                        DeliveryOutcome.INSERTED -> "Inserted transcript"
                        DeliveryOutcome.COPIED_TO_CLIPBOARD -> "Copied transcript"
                        DeliveryOutcome.NO_TEXT -> "No speech detected"
                    }
                    Log.i(TAG, "Transcription completed with outcome: $outcome")
                },
                onFailure = { error ->
                    statusText.text = "Transcription failed: ${error.message ?: "unknown"}"
                    Log.e(TAG, "Transcription failed", error)
                },
            )
        }
    }

    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
        currentAudioFile = null
        isRecording = false
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun copyToClipboard(value: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WhisperClient", value))
    }

    companion object {
        private const val TAG = "WhisperIme"
    }
}
