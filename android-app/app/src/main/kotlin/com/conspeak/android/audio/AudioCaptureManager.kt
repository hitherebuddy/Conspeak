package com.conspeak.android.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.conspeak.android.data.AudioSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages AudioRecord capture with configurable source, sample rate, and effects.
 * Delivers PCM frames via a callback for encoding and streaming.
 */
class AudioCaptureManager {

    companion object {
        private const val TAG = "AudioCapture"
    }

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var gainControl: AutomaticGainControl? = null
    private var captureJob: Job? = null
    private var gain: Float = 1.0f

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    var onPcmFrame: ((ShortArray, Int) -> Unit)? = null

    /**
     * Map mic source setting to AudioSource constant.
     */
    private fun mapMicSource(source: Int): Int = when (source) {
        0 -> MediaRecorder.AudioSource.DEFAULT
        1 -> MediaRecorder.AudioSource.MIC
        2 -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        3 -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        else -> MediaRecorder.AudioSource.MIC
    }

    fun start(settings: AudioSettings, scope: CoroutineScope) {
        if (_isRecording.value) return

        val sampleRate = settings.sampleRate
        val source = mapMicSource(settings.micSource)
        gain = settings.gain

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val frameSamples = sampleRate * settings.frameSizeMs / 1000
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
            frameSamples * 2 * 4 // at least 4 frames worth
        )

        try {
            audioRecord = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission not granted", e)
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return
        }

        val sessionId = audioRecord!!.audioSessionId

        // Apply effects
        if (settings.noiseSuppression && NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
        }
        if (settings.echoCancellation && AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true }
        }
        if (settings.autoGainControl && AutomaticGainControl.isAvailable()) {
            gainControl = AutomaticGainControl.create(sessionId)?.also { it.enabled = true }
        }

        audioRecord!!.startRecording()
        _isRecording.value = true

        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(frameSamples)
            while (isActive && _isRecording.value) {
                val read = audioRecord?.read(buffer, 0, frameSamples) ?: -1
                if (read > 0) {
                    // Apply software gain
                    if (gain != 1.0f) {
                        for (i in 0 until read) {
                            buffer[i] = (buffer[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
                        }
                    }

                    // Calculate level for meter
                    var maxSample = 0
                    for (i in 0 until read) {
                        val abs = kotlin.math.abs(buffer[i].toInt())
                        if (abs > maxSample) maxSample = abs
                    }
                    _level.value = maxSample / 32768f

                    // Deliver frame (silence if muted)
                    if (_isMuted.value) {
                        val silence = ShortArray(read)
                        onPcmFrame?.invoke(silence, read)
                    } else {
                        onPcmFrame?.invoke(buffer.copyOf(), read)
                    }
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord read error: $read")
                    break
                }
            }
        }
    }

    fun stop() {
        _isRecording.value = false
        captureJob?.cancel()
        captureJob = null

        noiseSuppressor?.release()
        noiseSuppressor = null
        echoCanceler?.release()
        echoCanceler = null
        gainControl?.release()
        gainControl = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        _level.value = 0f
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun setGain(value: Float) {
        gain = value.coerceIn(0f, 4f)
    }
}
