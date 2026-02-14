package com.conspeak.android.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.conspeak.android.ConspeakApp
import com.conspeak.android.MainActivity
import com.conspeak.android.R
import com.conspeak.android.audio.AudioCaptureManager
import com.conspeak.android.audio.OpusEncoderWrapper
import com.conspeak.android.data.AudioSettings
import com.conspeak.android.network.ConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground service that keeps audio capture and streaming alive in the background.
 * Uses foregroundServiceType="microphone" (Android 14+).
 */
class AudioStreamService : Service() {

    companion object {
        private const val TAG = "AudioStreamService"
        const val ACTION_MUTE = "com.conspeak.ACTION_MUTE"
        const val ACTION_UNMUTE = "com.conspeak.ACTION_UNMUTE"
        const val ACTION_STOP = "com.conspeak.ACTION_STOP"
        private const val NOTIFICATION_ID = 1001
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioStreamService = this@AudioStreamService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    val audioCaptureManager = AudioCaptureManager()
    val connectionManager = ConnectionManager()

    private var encoder: OpusEncoderWrapper? = null
    private var sequenceNumber = 0L
    private var sessionStartTime = 0L

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MUTE -> audioCaptureManager.setMuted(true)
            ACTION_UNMUTE -> audioCaptureManager.setMuted(false)
            ACTION_STOP -> stopStreaming()
            else -> {} // normal start
        }
        return START_STICKY
    }

    fun startStreaming(settings: AudioSettings, host: String, port: Int, trustedFp: String?) {
        startForeground()
        acquireWakeLock()

        // Initialize encoder
        encoder = OpusEncoderWrapper(
            sampleRate = settings.sampleRate,
            channels = 1,
            bitrate = settings.bitrate,
            frameSizeMs = settings.frameSizeMs
        )
        sequenceNumber = 0
        sessionStartTime = System.currentTimeMillis()

        // Set up audio capture -> encode -> send pipeline
        audioCaptureManager.onPcmFrame = { pcm, count ->
            val opusData = encoder?.encode(pcm)
            if (opusData != null && connectionManager.state.value == ConnectionManager.State.STREAMING) {
                val ts = System.currentTimeMillis() - sessionStartTime
                connectionManager.sendAudioFrame(sequenceNumber++, ts, opusData)
            }
        }

        // Connect
        connectionManager.onSessionAccepted = {
            _isStreaming.value = true
        }
        connectionManager.onDisconnected = {
            // Keep capturing - will resume when reconnected
        }

        connectionManager.connect(host, port, trustedFp, serviceScope)
        audioCaptureManager.start(settings, serviceScope)
    }

    fun stopStreaming() {
        _isStreaming.value = false
        audioCaptureManager.stop()
        connectionManager.disconnect()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForeground() {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AudioStreamService::class.java).apply { action = ACTION_MUTE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, AudioStreamService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ConspeakApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, connectionManager.peerName.value ?: "desktop"))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.action_mute), muteIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.action_stop), stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "conspeak:streaming")
        wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours max
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        stopStreaming()
        serviceScope.cancel()
        super.onDestroy()
    }
}
