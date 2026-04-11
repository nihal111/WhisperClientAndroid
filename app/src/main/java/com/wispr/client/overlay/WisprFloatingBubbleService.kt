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
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
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

class WhisperFloatingBubbleService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val serverClient = WhisperServerClient()

    private lateinit var transcriptStore: TranscriptStore
    private lateinit var serverConfigStore: ServerConfigStore
    private lateinit var overlayConfigStore: OverlayConfigStore

    private lateinit var windowManager: WindowManager
    private var bubbleView: LinearLayout? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var idleButton: ImageView? = null
    private var recordingPanel: LinearLayout? = null
    private var waveformView: WaveformView? = null
    private var submitButton: ImageView? = null
    private var cancelButton: ImageView? = null

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var isRecording = false
    private var hasEditableFocus = false
    private var isInForeground = false
    private var amplitudePollingJob: kotlinx.coroutines.Job? = null


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

        val density = resources.displayMetrics.density
        fun Int.dp() = (this * density + 0.5f).toInt()

        val container = DragInterceptLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val idle = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_mic_white))
            setBackgroundResource(R.drawable.bubble_idle_bg)
            layoutParams = LinearLayout.LayoutParams(52.dp(), 52.dp())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = 14.dp()
            setPadding(pad, pad, pad, pad)
            elevation = 6.dp().toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { startRecording() }
        }

        val cancel = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_close_white))
            setBackgroundResource(R.drawable.bubble_cancel_bg)
            layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = 10.dp()
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            setOnClickListener { cancelRecording() }
        }

        val waveform = WaveformView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                32.dp(),
            ).apply {
                marginStart = 8.dp()
                marginEnd = 8.dp()
            }
        }

        val submit = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_check_white))
            setBackgroundResource(R.drawable.bubble_submit_bg)
            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = 10.dp()
            setPadding(pad, pad, pad, pad)
            elevation = 2.dp().toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { stopAndSubmitRecording() }
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bubble_bar_bg)
            val hPad = 8.dp()
            val vPad = 6.dp()
            setPadding(hPad, vPad, hPad, vPad)
            elevation = 6.dp().toFloat()
            visibility = View.GONE
            addView(cancel)
            addView(waveform)
            addView(submit)
        }

        container.addView(idle)
        container.addView(controls)

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
        waveformView = waveform
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
        waveformView = null
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
            startAmplitudePolling()
            updateNotification("Recording...")
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start overlay recording", error)
            releaseRecorder(deleteTempAudio = true)
            setIdleMode()
            updateNotification("Record failed")
        }
    }

    private fun startAmplitudePolling() {
        amplitudePollingJob?.cancel()
        amplitudePollingJob = serviceScope.launch {
            while (isRecording) {
                val amplitude = mediaRecorder?.maxAmplitude ?: 0
                // Normalize 0..32767 to 0..1
                val normalized = amplitude.toFloat() / 32767f
                waveformView?.updateAmplitude(normalized)
                kotlinx.coroutines.delay(50) // 20 fps
            }
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
                    val didInsert = WhisperFocusAccessibilityService.getInstance()
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
        submitButton?.apply { isEnabled = true; alpha = 1f; isClickable = true }
        cancelButton?.apply { isEnabled = true; alpha = 1f; isClickable = true }
        waveformView?.stopAnimation()
        // Restore the saved position before snapping so the bubble returns
        // to the edge the user originally placed it on, not the shifted
        // position from the wider recording pill.
        bubbleLayoutParams?.let { params ->
            overlayConfigStore.getBubbleX()?.let { params.x = it }
            overlayConfigStore.getBubbleY()?.let { params.y = it }
        }
        bubbleView?.post { applySnapToEdge() }
    }

    private fun setRecordingMode() {
        idleButton?.visibility = View.GONE
        recordingPanel?.visibility = View.VISIBLE
        submitButton?.apply { isEnabled = true; alpha = 1f; isClickable = true }
        cancelButton?.apply { isEnabled = true; alpha = 1f; isClickable = true }
        bubbleView?.post { applyClampToScreen() }
    }

    private fun setTranscribingMode() {
        idleButton?.visibility = View.GONE
        recordingPanel?.visibility = View.VISIBLE
        submitButton?.apply { isEnabled = false; alpha = 0.4f; isClickable = false }
        cancelButton?.apply { isEnabled = false; alpha = 0.4f; isClickable = false }
        waveformView?.stopAnimation()
    }

    private fun startWaveformAnimation() {
        waveformView?.startAnimation()
    }

    private fun stopWaveformAnimation() {
        waveformView?.stopAnimation()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WhisperClient", text))
    }

    private fun releaseRecorder(deleteTempAudio: Boolean) {
        amplitudePollingJob?.cancel()
        amplitudePollingJob = null

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
        runCatching { windowManager.updateViewLayout(view, params) }
        overlayConfigStore.setBubblePosition(snapped.x, snapped.y)
    }

    private fun applyClampToScreen() {
        val view = bubbleView ?: return
        val params = bubbleLayoutParams ?: return
        val metrics = resources.displayMetrics
        val clamped = BubblePositioning.clampToScreen(
            rawX = params.x,
            rawY = params.y,
            bubbleWidth = view.width.coerceAtLeast(1),
            bubbleHeight = view.height.coerceAtLeast(1),
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            edgePadding = 24,
        )
        params.x = clamped.x
        params.y = clamped.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private inner class DragInterceptLayout(context: Context) : LinearLayout(context) {
        private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop * 2
        private var startX = 0
        private var startY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val params = bubbleLayoutParams ?: return false
                    startX = params.x
                    startY = params.y
                    initialTouchX = ev.rawX
                    initialTouchY = ev.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (ev.rawX - initialTouchX).toInt()
                    val deltaY = (ev.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(deltaX) > touchSlopPx || kotlin.math.abs(deltaY) > touchSlopPx) {
                        isDragging = true
                        return true
                    }
                }
            }
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val params = bubbleLayoutParams ?: return false
            val windowView = bubbleView ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    val metrics = resources.displayMetrics
                    val clamped = BubblePositioning.clampToScreen(
                        rawX = startX + deltaX,
                        rawY = startY + deltaY,
                        bubbleWidth = windowView.width.coerceAtLeast(1),
                        bubbleHeight = windowView.height.coerceAtLeast(1),
                        screenWidth = metrics.widthPixels,
                        screenHeight = metrics.heightPixels,
                        edgePadding = 24,
                    )
                    params.x = clamped.x
                    params.y = clamped.y
                    runCatching { windowManager.updateViewLayout(windowView, params) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        applySnapToEdge()
                    }
                    isDragging = false
                }
            }
            return true
        }
    }

    companion object {
        const val ACTION_START = "com.wispr.client.overlay.START"
        const val ACTION_STOP = "com.wispr.client.overlay.STOP"
        const val ACTION_FOCUS_EDITABLE = "com.wispr.client.overlay.FOCUS_EDITABLE"
        const val ACTION_FOCUS_NON_EDITABLE = "com.wispr.client.overlay.FOCUS_NON_EDITABLE"

        private const val TAG = "WhisperOverlaySvc"
        private const val CHANNEL_ID = "wispr_overlay_channel"
        private const val NOTIFICATION_ID = 2001
        fun sendCommand(context: Context, action: String) {
            val intent = Intent(context, WhisperFloatingBubbleService::class.java).setAction(action)
            try {
                context.startService(intent)
            } catch (error: IllegalStateException) {
                Log.w(TAG, "startService failed for action=$action, falling back to foreground", error)
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
