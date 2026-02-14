package com.conspeak.protocol

import java.util.TreeMap

/**
 * Adaptive jitter buffer for audio frames.
 * Buffers incoming frames by sequence number and releases them in order,
 * inserting silence for missing frames.
 *
 * @param initialDelayMs Initial buffer delay in milliseconds
 * @param frameSizeMs Duration of each audio frame in milliseconds
 * @param maxBufferMs Maximum buffer size before dropping old frames
 */
class JitterBuffer(
    private var initialDelayMs: Int = 60,
    private val frameSizeMs: Int = 20,
    private val maxBufferMs: Int = 500
) {
    private val buffer = TreeMap<Long, ByteArray>() // seq -> opus data
    private var nextExpectedSeq: Long = -1
    private var isBuffering = true
    private var bufferedMs = 0
    private var totalReceived = 0L
    private var totalLost = 0L
    private var totalLate = 0L

    /**
     * Add a frame to the buffer.
     */
    @Synchronized
    fun push(sequenceNumber: Long, opusData: ByteArray) {
        totalReceived++

        // If this is the first frame, set baseline
        if (nextExpectedSeq == -1L) {
            nextExpectedSeq = sequenceNumber
            isBuffering = true
            bufferedMs = 0
        }

        // Drop frames that are too old
        if (sequenceNumber < nextExpectedSeq) {
            totalLate++
            return
        }

        buffer[sequenceNumber] = opusData
        bufferedMs = ((buffer.lastKey() - nextExpectedSeq + 1) * frameSizeMs).toInt()

        // Trim if too large
        val maxFrames = maxBufferMs / frameSizeMs
        while (buffer.size > maxFrames) {
            val dropped = buffer.pollFirstEntry()
            if (dropped != null) {
                nextExpectedSeq = dropped.key + 1
            }
        }
    }

    /**
     * Pull the next frame from the buffer.
     * Returns the opus data, or null if a silence frame should be generated.
     * Returns EMPTY_RESULT if we're still buffering (caller should wait).
     */
    @Synchronized
    fun pull(): PullResult {
        if (nextExpectedSeq == -1L) return PullResult.Buffering

        // Initial buffering phase
        if (isBuffering) {
            bufferedMs = if (buffer.isEmpty()) 0
            else ((buffer.lastKey() - nextExpectedSeq + 1) * frameSizeMs).toInt()
            if (bufferedMs < initialDelayMs) {
                return PullResult.Buffering
            }
            isBuffering = false
        }

        val frame = buffer.remove(nextExpectedSeq)
        nextExpectedSeq++

        return if (frame != null) {
            PullResult.Frame(frame)
        } else {
            totalLost++
            PullResult.Missing // caller should use PLC or silence
        }
    }

    /**
     * Reset the buffer (e.g., on reconnect).
     */
    @Synchronized
    fun reset() {
        buffer.clear()
        nextExpectedSeq = -1
        isBuffering = true
        bufferedMs = 0
    }

    /**
     * Get current buffer depth in milliseconds.
     */
    @Synchronized
    fun bufferDepthMs(): Int {
        if (buffer.isEmpty()) return 0
        return ((buffer.lastKey() - (buffer.firstKey()) + 1) * frameSizeMs).toInt()
    }

    /**
     * Get packet loss ratio (0.0 to 1.0).
     */
    fun packetLossRatio(): Float {
        val total = totalReceived + totalLost
        if (total == 0L) return 0f
        return totalLost.toFloat() / total
    }

    fun setDelay(delayMs: Int) {
        initialDelayMs = delayMs
    }

    sealed class PullResult {
        data class Frame(val data: ByteArray) : PullResult() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Frame) return false
                return data.contentEquals(other.data)
            }
            override fun hashCode() = data.contentHashCode()
        }
        data object Missing : PullResult()
        data object Buffering : PullResult()
    }
}
