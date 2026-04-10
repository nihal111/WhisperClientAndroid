package com.wispr.client.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
    private var recordButton: Button? = null
    private var copyButton: Button? = null

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var isRecording = false
    private var hasEditableFocus = false

    override fun onCreate() {
        super.onCreate()
        transcriptStore = TranscriptStore(this)
        serverConfigStore = ServerConfigStore(this)
        overlayConfigStore = OverlayConfigStore(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Idle"))
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
                hideBubble()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseRecorder()
        hideBubble()
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
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xCC1E1E1E.toInt())
        }
        val record = Button(this).apply {
            text = "Record"
            setOnClickListener {
                if (!isRecording) {
                    startRecording()
                } else {
                    stopAndTranscribe()
                }
            }
        }
        val copy = Button(this).apply {
            text = "Copy"
            setOnClickListener {
                copyLastTranscript()
            }
        }
        container.addView(record)
        container.addView(copy)
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
        container.setOnTouchListener(DragTouchListener())
        windowManager.addView(container, params)
        bubbleView = container
        bubbleLayoutParams = params
        recordButton = record
        copyButton = copy
    }

    private fun hideBubble() {
        val view = bubbleView ?: return
        runCatching { windowManager.removeView(view) }
        bubbleView = null
        bubbleLayoutParams = null
        recordButton = null
        copyButton = null
    }

    private fun startRecording() {
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
            recordButton?.text = "Stop"
            updateNotification("Recording...")
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start overlay recording", error)
            releaseRecorder()
            updateNotification("Record failed")
        }
    }

    private fun stopAndTranscribe() {
        val recorder = mediaRecorder
        val audioFile = currentAudioFile
        if (recorder == null || audioFile == null) {
            releaseRecorder()
            return
        }
        runCatching { recorder.stop() }
        releaseRecorder()
        recordButton?.text = "..."
        updateNotification("Transcribing...")
        val baseUrl = serverConfigStore.getBaseUrl()
        val allowInsecureHttps = serverConfigStore.getAllowInsecureHttps()

        serviceScope.launch {
            val result = serverClient.transcribeAudio(baseUrl, audioFile, allowInsecureHttps)
            if (!audioFile.delete()) {
                Log.w(TAG, "Failed deleting temp file: ${audioFile.absolutePath}")
            }
            result.fold(
                onSuccess = { text ->
                    transcriptStore.setLastTranscript(text)
                    val didInsert = WisprFocusAccessibilityService.getInstance()
                        ?.insertTextIntoFocusedField(text)
                        ?: false
                    if (!didInsert) {
                        copyToClipboard(text)
                    }
                    recordButton?.text = "Record"
                    updateNotification(if (didInsert) "Inserted transcript" else "Copied transcript")
                },
                onFailure = { error ->
                    recordButton?.text = "Record"
                    Log.e(TAG, "Overlay transcription failed", error)
                    updateNotification("Transcription failed")
                },
            )
        }
    }

    private fun copyLastTranscript() {
        val text = transcriptStore.getLastTranscript()
        if (text.isBlank()) {
            return
        }
        copyToClipboard(text)
        updateNotification("Copied transcript")
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WhisperClient", text))
    }

    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
        currentAudioFile = null
        isRecording = false
        recordButton?.text = "Record"
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
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
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
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        applySnapToEdge()
                        true
                    } else {
                        false
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

        fun sendCommand(context: Context, action: String) {
            val intent = Intent(context, WisprFloatingBubbleService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
