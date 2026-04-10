package com.wispr.client.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wispr.client.R
import com.wispr.client.data.ServerConfigStore
import com.wispr.client.data.TranscriptStore
import com.wispr.client.network.WisprServerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class WisprFloatingBubbleService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val serverClient = WisprServerClient()

    private lateinit var transcriptStore: TranscriptStore
    private lateinit var serverConfigStore: ServerConfigStore
    private lateinit var overlayConfigStore: OverlayConfigStore

    private lateinit var windowManager: WindowManager
    private var bubbleView: LinearLayout? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var idleButton: Button? = null
    private var recordingPanel: LinearLayout? = null
    private var waveformText: TextView? = null
    private var submitButton: Button? = null
    private var cancelButton: Button? = null

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var isRecording = false
    private var hasEditableFocus = false
    private var isInForeground = false

    private var waveformJob = serviceScope.launch { }

    override fun onCreate() {
        super.onCreate()
        transcriptStore = TranscriptStore(this)
        serverConfigStore = ServerConfigStore(this)
        overlayConfigStore = OverlayConfigStore(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!OverlayPermission.canDraw(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (hasEditableFocus) {
                    showBubbleIfNeeded()
                }
            }
            ACTION_STOP -> {
                hideBubble()
                stopSelf()
            }
            ACTION_FOCUS_EDITABLE -> {
                hasEditableFocus = true
                if (OverlayPermission.canDraw(this)) {
                    showBubbleIfNeeded()
                }
            }
            ACTION_FOCUS_NON_EDITABLE -> {
                hasEditableFocus = false
                if (!isRecording) {
                    hideBubble()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseRecorder(deleteTempAudio = true)
        stopWaveformAnimation()
        hideBubble()
        exitForegroundIfNeeded()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubbleIfNeeded() {
        if (bubbleView != null) {
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xCC1E1E1E.toInt())
        }

        val idle = Button(this).apply {
            text = "Mic"
            isAllCaps = false
            setOnClickListener { startRecording() }
        }

        val waveform = TextView(this).apply {
            text = WAVE_FRAMES.first()
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setPadding(12, 0, 12, 0)
        }

        val submit = Button(this).apply {
            text = "✓"
            isAllCaps = false
            textSize = 20f
            setOnClickListener { stopAndSubmitRecording() }
        }

        val cancel = Button(this).apply {
            text = "✕"
            isAllCaps = false
            textSize = 20f
            setOnClickListener { cancelRecording() }
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(waveform)
            addView(submit)
            addView(cancel)
        }

        container.addView(idle)
        container.addView(controls)
        val dragTouchListener = DragTouchListener()
        container.setOnTouchListener(dragTouchListener)
        idle.setOnTouchListener(dragTouchListener)
        controls.setOnTouchListener(dragTouchListener)
        waveform.setOnTouchListener(dragTouchListener)
        submit.setOnTouchListener(dragTouchListener)
        cancel.setOnTouchListener(dragTouchListener)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = overlayConfigStore.getBubbleX() ?: defaultStartX()
            y = overlayConfigStore.getBubbleY() ?: defaultStartY()
        }

        windowManager.addView(container, params)

        bubbleView = container
        bubbleLayoutParams = params
        idleButton = idle
        recordingPanel = controls
        waveformText = waveform
        submitButton = submit
        cancelButton = cancel

        container.post { applySnapToEdge() }
        setIdleMode()
    }

    private fun hideBubble() {
        val view = bubbleView ?: return
        runCatching { windowManager.removeView(view) }
        bubbleView = null
        bubbleLayoutParams = null
        idleButton = null
        recordingPanel = null
        waveformText = null
        submitButton = null
        cancelButton = null
    }

    private fun startRecording() {
        if (!hasRecordAudioPermission()) {
            updateNotification("Mic permission required")
            return
        }

        try {
            val outputFile = File(cacheDir, "bubble-recording-${System.currentTimeMillis()}.webm")
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
            enterForegroundIfNeeded("Recording...")
            setRecordingMode()
            startWaveformAnimation()
            updateNotification("Recording...")
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start overlay recording", error)
            releaseRecorder(deleteTempAudio = true)
            setIdleMode()
            updateNotification("Record failed")
        }
    }

    private fun cancelRecording() {
        if (!isRecording) {
            setIdleMode()
            return
        }

        runCatching { mediaRecorder?.stop() }
        releaseRecorder(deleteTempAudio = true)
        stopWaveformAnimation()
        setIdleMode()
        updateNotification("Recording cancelled")
        exitForegroundIfNeeded()

        if (!hasEditableFocus) {
            hideBubble()
        }
    }

    private fun stopAndSubmitRecording() {
        val recorder = mediaRecorder
        val audioFile = currentAudioFile
        if (recorder == null || audioFile == null) {
            releaseRecorder(deleteTempAudio = true)
            setIdleMode()
            return
        }

        runCatching { recorder.stop() }
        runCatching { recorder.release() }
        mediaRecorder = null
        isRecording = false
        stopWaveformAnimation()
        setTranscribingMode()
        updateNotification("Transcribing...")

        val baseUrl = serverConfigStore.getBaseUrl()
        val allowInsecureHttps = serverConfigStore.getAllowInsecureHttps()

        serviceScope.launch {
            val result = serverClient.transcribeAudio(baseUrl, audioFile, allowInsecureHttps)
            if (!audioFile.delete()) {
                Log.w(TAG, "Failed deleting temp file: ${audioFile.absolutePath}")
            }
            currentAudioFile = null

            result.fold(
                onSuccess = { text ->
                    transcriptStore.setLastTranscript(text)
                    val didInsert = WisprFocusAccessibilityService.getInstance()
                        ?.insertTextIntoFocusedField(text)
                        ?: false
                    if (!didInsert) {
                        copyToClipboard(text)
                    }
                    setIdleMode()
                    updateNotification(if (didInsert) "Inserted transcript" else "Copied transcript")
                    exitForegroundIfNeeded()
                    if (!hasEditableFocus) {
                        hideBubble()
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Overlay transcription failed", error)
                    setIdleMode()
                    updateNotification("Transcription failed")
                    exitForegroundIfNeeded()
                    if (!hasEditableFocus) {
                        hideBubble()
                    }
                },
            )
        }
    }

    private fun setIdleMode() {
        idleButton?.visibility = View.VISIBLE
        recordingPanel?.visibility = View.GONE
        submitButton?.isEnabled = true
        cancelButton?.isEnabled = true
        waveformText?.text = WAVE_FRAMES.first()
    }

    private fun setRecordingMode() {
        idleButton?.visibility = View.GONE
        recordingPanel?.visibility = View.VISIBLE
        submitButton?.isEnabled = true
        cancelButton?.isEnabled = true
    }

    private fun setTranscribingMode() {
        idleButton?.visibility = View.GONE
        recordingPanel?.visibility = View.VISIBLE
        submitButton?.isEnabled = false
        cancelButton?.isEnabled = false
        waveformText?.text = "…"
    }

    private fun startWaveformAnimation() {
        stopWaveformAnimation()
        waveformJob = serviceScope.launch {
            var frame = 0
            while (isActive && isRecording) {
                waveformText?.text = WAVE_FRAMES[frame % WAVE_FRAMES.size]
                frame += 1
                delay(120)
            }
        }
    }

    private fun stopWaveformAnimation() {
        waveformJob.cancel()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WhisperClient", text))
    }

    private fun releaseRecorder(deleteTempAudio: Boolean) {
        mediaRecorder?.let { recorder ->
            runCatching { recorder.release() }
        }
        mediaRecorder = null
        isRecording = false

        if (deleteTempAudio) {
            currentAudioFile?.let { tempFile ->
                if (tempFile.exists() && !tempFile.delete()) {
                    Log.w(TAG, "Failed deleting temp file: ${tempFile.absolutePath}")
                }
            }
            currentAudioFile = null
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(status: String) {
        if (!isInForeground) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun enterForegroundIfNeeded(status: String) {
        if (isInForeground) {
            updateNotification(status)
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification(status))
        isInForeground = true
    }

    private fun exitForegroundIfNeeded() {
        if (!isInForeground) {
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        isInForeground = false
    }

    private fun defaultStartX(): Int {
        val displayMetrics = resources.displayMetrics
        return (displayMetrics.widthPixels * 0.75f).toInt()
    }

    private fun defaultStartY(): Int {
        val displayMetrics = resources.displayMetrics
        return (displayMetrics.heightPixels * 0.45f).toInt()
    }

    private fun applySnapToEdge() {
        val view = bubbleView ?: return
        val params = bubbleLayoutParams ?: return
        val metrics = resources.displayMetrics
        val snapped = BubblePositioning.snapToEdge(
            rawX = params.x,
            rawY = params.y,
            bubbleWidth = view.width.coerceAtLeast(1),
            bubbleHeight = view.height.coerceAtLeast(1),
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            edgePadding = 24,
        )
        params.x = snapped.x
        params.y = snapped.y
        windowManager.updateViewLayout(view, params)
        overlayConfigStore.setBubblePosition(snapped.x, snapped.y)
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = bubbleLayoutParams ?: return false
            val windowView = bubbleView ?: return false
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(deltaX) > 6 || kotlin.math.abs(deltaY) > 6) {
                        moved = true
                    }
                    params.x = startX + deltaX
                    params.y = startY + deltaY
                    runCatching { windowManager.updateViewLayout(windowView, params) }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        applySnapToEdge()
                        true
                    } else {
                        view.performClick()
                        true
                    }
                }
                else -> false
            }
        }
    }

    companion object {
        const val ACTION_START = "com.wispr.client.overlay.START"
        const val ACTION_STOP = "com.wispr.client.overlay.STOP"
        const val ACTION_FOCUS_EDITABLE = "com.wispr.client.overlay.FOCUS_EDITABLE"
        const val ACTION_FOCUS_NON_EDITABLE = "com.wispr.client.overlay.FOCUS_NON_EDITABLE"

        private const val TAG = "WisprOverlaySvc"
        private const val CHANNEL_ID = "wispr_overlay_channel"
        private const val NOTIFICATION_ID = 2001
        private val WAVE_FRAMES = listOf("▁▂▃▅▇▅▃▂", "▂▃▅▇▅▃▂▁", "▃▅▇▅▃▂▁▂", "▅▇▅▃▂▁▂▃")

        fun sendCommand(context: Context, action: String) {
            val intent = Intent(context, WisprFloatingBubbleService::class.java).setAction(action)
            try {
                context.startService(intent)
            } catch (error: IllegalStateException) {
                Log.w(TAG, "startService failed for action=$action, falling back to foreground", error)
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
