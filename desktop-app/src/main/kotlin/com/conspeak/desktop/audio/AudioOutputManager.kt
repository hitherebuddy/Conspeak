package com.conspeak.desktop.audio

import com.conspeak.protocol.AudioFrame
import com.conspeak.protocol.Constants
import com.conspeak.protocol.JitterBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.concentus.OpusApplication
import org.concentus.OpusDecoder
import org.slf4j.LoggerFactory
import javax.sound.sampled.*

/**
 * Manages audio output to a virtual audio device (e.g., VB-CABLE Input).
 * Receives Opus frames, decodes to PCM, buffers via jitter buffer, and writes
 * to the selected audio output device.
 */
class AudioOutputManager {

    private val log = LoggerFactory.getLogger("AudioOutput")

    private var decoder: OpusDecoder? = null
    private var sourceDataLine: SourceDataLine? = null
    private var outputJob: Job? = null
    private val jitterBuffer = JitterBuffer()

    private var sampleRate = Constants.DEFAULT_SAMPLE_RATE
    private var frameSizeMs = Constants.DEFAULT_FRAME_SIZE_MS
    private var frameSizeSamples = sampleRate * frameSizeMs / 1000

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    private val _selectedDevice = MutableStateFlow<String?>(null)
    val selectedDevice: StateFlow<String?> = _selectedDevice

    private var isMuted = false
    private var volume = 1.0f

    /**
     * Get available audio output devices (mixers) that support the required format.
     * Filters for devices that likely represent virtual cables or output endpoints.
     */
    fun getAvailableDevices(): List<String> {
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val lineInfo = DataLine.Info(SourceDataLine::class.java, format)

        return AudioSystem.getMixerInfo()
            .filter { info ->
                try {
                    val mixer = AudioSystem.getMixer(info)
                    mixer.isLineSupported(lineInfo)
                } catch (_: Exception) { false }
            }
            .map { it.name }
    }

    /**
     * Find the VB-CABLE input device automatically.
     */
    fun findVbCableDevice(): String? {
        return getAvailableDevices().find { name ->
            name.contains("CABLE", ignoreCase = true) &&
            name.contains("Input", ignoreCase = true)
        } ?: getAvailableDevices().find { name ->
            name.contains("CABLE", ignoreCase = true)
        }
    }

    /**
     * Start the audio output pipeline.
     */
    fun start(
        sampleRate: Int,
        frameSizeMs: Int,
        jitterBufferMs: Int,
        deviceName: String?,
        scope: CoroutineScope
    ) {
        this.sampleRate = sampleRate
        this.frameSizeMs = frameSizeMs
        this.frameSizeSamples = sampleRate * frameSizeMs / 1000

        // Initialize Opus decoder
        decoder = OpusDecoder(sampleRate, 1)

        // Configure jitter buffer
        jitterBuffer.reset()
        jitterBuffer.setDelay(jitterBufferMs)

        // Open audio output line
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val lineInfo = DataLine.Info(SourceDataLine::class.java, format)

        val mixer = if (deviceName != null) {
            AudioSystem.getMixerInfo()
                .find { it.name == deviceName }
                ?.let { AudioSystem.getMixer(it) }
        } else null

        sourceDataLine = if (mixer != null) {
            mixer.getLine(lineInfo) as SourceDataLine
        } else {
            AudioSystem.getLine(lineInfo) as SourceDataLine
        }

        // Buffer size: 4 frames worth
        val bufferBytes = frameSizeSamples * 2 * 4
        sourceDataLine!!.open(format, bufferBytes)
        sourceDataLine!!.start()

        _selectedDevice.value = deviceName ?: "Default"
        _isPlaying.value = true

        log.info("Audio output started: ${sampleRate}Hz, ${frameSizeMs}ms frames, device=$deviceName")

        // Start playback loop
        outputJob = scope.launch(Dispatchers.IO) {
            val silenceFrame = ShortArray(frameSizeSamples)
            val decodeBuffer = ShortArray(frameSizeSamples)

            while (isActive && _isPlaying.value) {
                when (val result = jitterBuffer.pull()) {
                    is JitterBuffer.PullResult.Frame -> {
                        // Decode Opus to PCM
                        val pcm = try {
                            val decoded = decoder!!.decode(
                                result.data, 0, result.data.size,
                                decodeBuffer, 0, frameSizeSamples, false
                            )
                            if (decoded > 0) decodeBuffer.copyOf(decoded) else silenceFrame
                        } catch (e: Exception) {
                            log.debug("Decode error, using silence", e)
                            silenceFrame
                        }
                        writePcm(pcm)
                    }

                    is JitterBuffer.PullResult.Missing -> {
                        // Packet loss concealment: decode null for PLC
                        val pcm = try {
                            val decoded = decoder!!.decode(
                                null, 0, 0,
                                decodeBuffer, 0, frameSizeSamples, true
                            )
                            if (decoded > 0) decodeBuffer.copyOf(decoded) else silenceFrame
                        } catch (_: Exception) {
                            silenceFrame
                        }
                        writePcm(pcm)
                    }

                    is JitterBuffer.PullResult.Buffering -> {
                        // Still buffering, wait a bit
                        delay(frameSizeMs.toLong() / 2)
                    }
                }
            }
        }
    }

    private fun writePcm(pcm: ShortArray) {
        if (isMuted) {
            val silence = ByteArray(pcm.size * 2)
            sourceDataLine?.write(silence, 0, silence.size)
            _level.value = 0f
            return
        }

        // Apply volume and convert to bytes
        val bytes = ByteArray(pcm.size * 2)
        var maxSample = 0
        for (i in pcm.indices) {
            val sample = (pcm[i] * volume).toInt().coerceIn(-32768, 32767).toShort()
            val abs = kotlin.math.abs(sample.toInt())
            if (abs > maxSample) maxSample = abs
            // Little-endian
            bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (sample.toInt() shr 8 and 0xFF).toByte()
        }
        _level.value = maxSample / 32768f

        sourceDataLine?.write(bytes, 0, bytes.size)
    }

    /**
     * Feed an incoming audio frame into the jitter buffer.
     */
    fun feedFrame(frame: AudioFrame) {
        jitterBuffer.push(frame.sequenceNumber, frame.opusData)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 2f)
    }

    fun getJitterBufferDepthMs(): Int = jitterBuffer.bufferDepthMs()
    fun getPacketLossRatio(): Float = jitterBuffer.packetLossRatio()

    fun stop() {
        _isPlaying.value = false
        outputJob?.cancel()
        sourceDataLine?.stop()
        sourceDataLine?.close()
        sourceDataLine = null
        decoder = null
        jitterBuffer.reset()
        _level.value = 0f
    }
}
