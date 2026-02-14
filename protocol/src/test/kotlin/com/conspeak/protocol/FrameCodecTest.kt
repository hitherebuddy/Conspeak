package com.conspeak.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FrameCodecTest {

    @Test
    fun `encode and decode PairRequest`() {
        val msg = PairRequest("Pixel 8", "abcdef1234567890")
        val encoded = FrameCodec.encode(msg)

        val input = ByteArrayInputStream(encoded)
        val decoded = FrameCodec.readMessage(input)

        assertNotNull(decoded)
        assertTrue(decoded is PairRequest)
        assertEquals("Pixel 8", decoded.phoneName)
        assertEquals("abcdef1234567890", decoded.phoneCertFingerprint)
    }

    @Test
    fun `encode and decode PairChallenge`() {
        val msg = PairChallenge("My Desktop", "fedcba0987654321")
        val encoded = FrameCodec.encode(msg)

        val input = ByteArrayInputStream(encoded)
        val decoded = FrameCodec.readMessage(input)

        assertNotNull(decoded)
        assertTrue(decoded is PairChallenge)
        assertEquals("My Desktop", decoded.desktopName)
        assertEquals("fedcba0987654321", decoded.desktopCertFingerprint)
    }

    @Test
    fun `encode and decode AudioFrame`() {
        val opusData = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val msg = AudioFrame(42, 12345678L, opusData)
        val encoded = FrameCodec.encode(msg)

        val input = ByteArrayInputStream(encoded)
        val decoded = FrameCodec.readMessage(input)

        assertNotNull(decoded)
        assertTrue(decoded is AudioFrame)
        assertEquals(42L, decoded.sequenceNumber)
        assertEquals(12345678L, decoded.timestampMs)
        assertTrue(opusData.contentEquals(decoded.opusData))
    }

    @Test
    fun `encode and decode SessionRequest`() {
        val msg = SessionRequest("OPUS", 48000, 1, 64000, 20)
        val encoded = FrameCodec.encode(msg)

        val input = ByteArrayInputStream(encoded)
        val decoded = FrameCodec.readMessage(input)

        assertNotNull(decoded)
        assertTrue(decoded is SessionRequest)
        assertEquals("OPUS", decoded.codec)
        assertEquals(48000, decoded.sampleRate)
        assertEquals(1, decoded.channels)
        assertEquals(64000, decoded.bitrate)
        assertEquals(20, decoded.frameSizeMs)
    }

    @Test
    fun `encode and decode Keepalive`() {
        val encoded = FrameCodec.encode(Keepalive)
        val input = ByteArrayInputStream(encoded)
        val decoded = FrameCodec.readMessage(input)

        assertNotNull(decoded)
        assertTrue(decoded is Keepalive)
    }

    @Test
    fun `encode and decode StatusUpdate`() {
        val msg = StatusUpdate("streaming", 45, 0.02f, "all good")
        val encoded = FrameCodec.encode(msg)

        val input = ByteArrayInputStream(encoded)
        val decoded = FrameCodec.readMessage(input)

        assertNotNull(decoded)
        assertTrue(decoded is StatusUpdate)
        assertEquals("streaming", decoded.state)
        assertEquals(45, decoded.latencyMs)
        assertEquals(0.02f, decoded.packetLoss, 0.001f)
        assertEquals("all good", decoded.extra)
    }

    @Test
    fun `writeMessage and readMessage via streams`() {
        val messages = listOf(
            PairRequest("Phone", "fp1"),
            PairChallenge("Desktop", "fp2"),
            PairConfirm(true),
            PairComplete(true),
            SessionRequest("OPUS", 48000, 1, 64000, 20),
            SessionAccept("OPUS", 48000, 1, 64000, 20),
            AudioFrame(0, 0, byteArrayOf(1, 2, 3)),
            Keepalive,
            KeepaliveAck,
            StatusUpdate("ok", 10, 0f, "")
        )

        val baos = ByteArrayOutputStream()
        for (msg in messages) {
            FrameCodec.writeMessage(baos, msg)
        }

        val bais = ByteArrayInputStream(baos.toByteArray())
        for (original in messages) {
            val decoded = FrameCodec.readMessage(bais)
            assertNotNull(decoded, "Failed to decode ${original.type}")
            assertEquals(original.type, decoded.type)
        }
    }

    @Test
    fun `header format is correct`() {
        val msg = Keepalive
        val encoded = FrameCodec.encode(msg)

        // Type byte
        assertEquals(MessageType.KEEPALIVE.code, encoded[0])
        // Length bytes (should be 0 for keepalive)
        assertEquals(0.toByte(), encoded[1])
        assertEquals(0.toByte(), encoded[2])
        assertEquals(0.toByte(), encoded[3])
        // Total size should be 4 (header only)
        assertEquals(4, encoded.size)
    }
}
