package com.conspeak.android.audio

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import io.github.jaredmdobson.concentus.OpusSignal

/**
 * Wraps the Concentus (pure Java) Opus encoder.
 */
class OpusEncoderWrapper(
    private val sampleRate: Int = 48000,
    private val channels: Int = 1,
    bitrate: Int = 64000,
    private val frameSizeMs: Int = 20
) {
    private val encoder: OpusEncoder = OpusEncoder(sampleRate, channels, OpusApplication.OPUS_APPLICATION_VOIP)
    private val frameSizeSamples = sampleRate * frameSizeMs / 1000
    private val outputBuffer = ByteArray(4000) // max opus frame

    init {
        encoder.bitrate = bitrate
        encoder.signalType = OpusSignal.OPUS_SIGNAL_VOICE
        encoder.complexity = 5 // balanced CPU/quality
        encoder.useVBR = true
    }

    /**
     * Encode a PCM frame (16-bit mono) to Opus.
     * @param pcm PCM samples (exactly frameSizeSamples)
     * @return Opus encoded bytes, or null on error
     */
    fun encode(pcm: ShortArray): ByteArray? {
        return try {
            val len = encoder.encode(pcm, 0, frameSizeSamples, outputBuffer, 0, outputBuffer.size)
            if (len > 0) outputBuffer.copyOfRange(0, len) else null
        } catch (e: Exception) {
            null
        }
    }

    fun setBitrate(bitrate: Int) {
        encoder.bitrate = bitrate
    }

    fun setComplexity(complexity: Int) {
        encoder.complexity = complexity.coerceIn(0, 10)
    }

    fun getFrameSizeSamples(): Int = frameSizeSamples
}
