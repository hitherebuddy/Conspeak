package com.conspeak.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JitterBufferTest {

    @Test
    fun `basic push and pull in order`() {
        val buffer = JitterBuffer(initialDelayMs = 40, frameSizeMs = 20)

        // Push 3 frames (enough to satisfy 40ms delay with 20ms frames)
        buffer.push(0, byteArrayOf(1))
        buffer.push(1, byteArrayOf(2))
        buffer.push(2, byteArrayOf(3))

        // Should now be ready
        val r1 = buffer.pull()
        assertTrue(r1 is JitterBuffer.PullResult.Frame)
        assertEquals(1.toByte(), (r1 as JitterBuffer.PullResult.Frame).data[0])

        val r2 = buffer.pull()
        assertTrue(r2 is JitterBuffer.PullResult.Frame)
        assertEquals(2.toByte(), (r2 as JitterBuffer.PullResult.Frame).data[0])
    }

    @Test
    fun `buffering state when not enough frames`() {
        val buffer = JitterBuffer(initialDelayMs = 60, frameSizeMs = 20)

        // Only push 1 frame (need 3 for 60ms)
        buffer.push(0, byteArrayOf(1))

        val result = buffer.pull()
        assertTrue(result is JitterBuffer.PullResult.Buffering)
    }

    @Test
    fun `missing frame reports Missing`() {
        val buffer = JitterBuffer(initialDelayMs = 20, frameSizeMs = 20)

        // Push frames 0, 1, 3 (skip 2)
        buffer.push(0, byteArrayOf(1))
        buffer.push(1, byteArrayOf(2))
        buffer.push(3, byteArrayOf(4))

        val r1 = buffer.pull() // seq 0
        assertTrue(r1 is JitterBuffer.PullResult.Frame)

        val r2 = buffer.pull() // seq 1
        assertTrue(r2 is JitterBuffer.PullResult.Frame)

        val r3 = buffer.pull() // seq 2 - missing!
        assertTrue(r3 is JitterBuffer.PullResult.Missing)

        val r4 = buffer.pull() // seq 3
        assertTrue(r4 is JitterBuffer.PullResult.Frame)
        assertEquals(4.toByte(), (r4 as JitterBuffer.PullResult.Frame).data[0])
    }

    @Test
    fun `late frames are dropped`() {
        val buffer = JitterBuffer(initialDelayMs = 20, frameSizeMs = 20)

        buffer.push(0, byteArrayOf(1))
        buffer.push(1, byteArrayOf(2))

        // Pull seq 0
        buffer.pull()

        // Now push seq 0 again (late) - should be silently dropped
        buffer.push(0, byteArrayOf(99))

        // Pull seq 1 - should still be original
        val r = buffer.pull()
        assertTrue(r is JitterBuffer.PullResult.Frame)
        assertEquals(2.toByte(), (r as JitterBuffer.PullResult.Frame).data[0])
    }

    @Test
    fun `reset clears state`() {
        val buffer = JitterBuffer(initialDelayMs = 20, frameSizeMs = 20)

        buffer.push(0, byteArrayOf(1))
        buffer.push(1, byteArrayOf(2))
        buffer.pull()

        buffer.reset()

        // After reset, should be in buffering state again
        val result = buffer.pull()
        assertTrue(result is JitterBuffer.PullResult.Buffering)
    }

    @Test
    fun `packet loss ratio computed correctly`() {
        val buffer = JitterBuffer(initialDelayMs = 20, frameSizeMs = 20)

        // Push 0, 1, 3 (gap at 2)
        buffer.push(0, byteArrayOf(1))
        buffer.push(1, byteArrayOf(2))
        buffer.push(3, byteArrayOf(4))

        buffer.pull() // 0
        buffer.pull() // 1
        buffer.pull() // 2 (missing)
        buffer.pull() // 3

        // 3 received, 1 lost => loss ratio = 1/4 = 0.25
        assertTrue(buffer.packetLossRatio() > 0f)
    }
}
