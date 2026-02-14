package com.conspeak.protocol

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Reads and writes framed messages on a stream.
 *
 * Wire format per message:
 *   [type: 1 byte] [length: 3 bytes big-endian] [payload: length bytes]
 */
object FrameCodec {

    private const val HEADER_SIZE = 4 // 1 (type) + 3 (length)
    private const val MAX_PAYLOAD = 16 * 1024 * 1024 // 16 MB

    /**
     * Encode a Message into its wire bytes (header + payload).
     */
    fun encode(message: Message): ByteArray {
        val payload = message.encodePayload()
        require(payload.size <= MAX_PAYLOAD) { "Payload too large: ${payload.size}" }
        val frame = ByteArray(HEADER_SIZE + payload.size)
        frame[0] = message.type.code
        // 3-byte big-endian length
        frame[1] = ((payload.size shr 16) and 0xFF).toByte()
        frame[2] = ((payload.size shr 8) and 0xFF).toByte()
        frame[3] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, frame, HEADER_SIZE, payload.size)
        return frame
    }

    /**
     * Write a message to an output stream.
     */
    fun writeMessage(out: OutputStream, message: Message) {
        out.write(encode(message))
        out.flush()
    }

    /**
     * Read exactly n bytes from input stream. Throws on EOF.
     */
    private fun readFully(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = input.read(buf, offset, n - offset)
            if (read == -1) throw java.io.EOFException("Unexpected end of stream")
            offset += read
        }
        return buf
    }

    /**
     * Read a framed message from an input stream. Blocks until a full message
     * is available. Returns null for unknown message types (forward compat).
     */
    fun readMessage(input: InputStream): Message? {
        val header = readFully(input, HEADER_SIZE)
        val typeCode = header[0]
        val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)

        require(length in 0..MAX_PAYLOAD) { "Invalid payload length: $length" }

        val payload = if (length > 0) readFully(input, length) else ByteArray(0)
        val msgType = MessageType.fromCode(typeCode) ?: return null // skip unknown

        return decodeMessage(msgType, payload)
    }

    /**
     * Decode a payload into the appropriate Message subtype.
     */
    fun decodeMessage(type: MessageType, payload: ByteArray): Message {
        return when (type) {
            MessageType.PAIR_REQUEST -> PairRequest.decode(payload)
            MessageType.PAIR_CHALLENGE -> PairChallenge.decode(payload)
            MessageType.PAIR_CONFIRM -> PairConfirm.decode(payload)
            MessageType.PAIR_COMPLETE -> PairComplete.decode(payload)
            MessageType.SESSION_REQUEST -> SessionRequest.decode(payload)
            MessageType.SESSION_ACCEPT -> SessionAccept.decode(payload)
            MessageType.SESSION_REJECT -> SessionReject.decode(payload)
            MessageType.SESSION_END -> SessionEnd
            MessageType.AUDIO_FRAME -> AudioFrame.decode(payload)
            MessageType.AUDIO_CONFIG -> AudioConfig.decode(payload)
            MessageType.KEEPALIVE -> Keepalive
            MessageType.KEEPALIVE_ACK -> KeepaliveAck
            MessageType.STATUS_UPDATE -> StatusUpdate.decode(payload)
        }
    }
}
