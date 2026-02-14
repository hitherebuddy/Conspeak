package com.conspeak.protocol

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Base sealed class for all protocol messages.
 */
sealed class Message {
    abstract val type: MessageType
    abstract fun encodePayload(): ByteArray
}

// --- Pairing Messages ---

data class PairRequest(
    val phoneName: String,
    val phoneCertFingerprint: String
) : Message() {
    override val type = MessageType.PAIR_REQUEST
    override fun encodePayload(): ByteArray {
        val nameBytes = phoneName.toByteArray(StandardCharsets.UTF_8)
        val fpBytes = phoneCertFingerprint.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(2 + nameBytes.size + 2 + fpBytes.size)
        buf.putShort(nameBytes.size.toShort())
        buf.put(nameBytes)
        buf.putShort(fpBytes.size.toShort())
        buf.put(fpBytes)
        return buf.array()
    }

    companion object {
        fun decode(payload: ByteArray): PairRequest {
            val buf = ByteBuffer.wrap(payload)
            val nameLen = buf.short.toInt() and 0xFFFF
            val nameBytes = ByteArray(nameLen)
            buf.get(nameBytes)
            val fpLen = buf.short.toInt() and 0xFFFF
            val fpBytes = ByteArray(fpLen)
            buf.get(fpBytes)
            return PairRequest(
                String(nameBytes, StandardCharsets.UTF_8),
                String(fpBytes, StandardCharsets.UTF_8)
            )
        }
    }
}

data class PairChallenge(
    val desktopName: String,
    val desktopCertFingerprint: String
) : Message() {
    override val type = MessageType.PAIR_CHALLENGE
    override fun encodePayload(): ByteArray {
        val nameBytes = desktopName.toByteArray(StandardCharsets.UTF_8)
        val fpBytes = desktopCertFingerprint.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(2 + nameBytes.size + 2 + fpBytes.size)
        buf.putShort(nameBytes.size.toShort())
        buf.put(nameBytes)
        buf.putShort(fpBytes.size.toShort())
        buf.put(fpBytes)
        return buf.array()
    }

    companion object {
        fun decode(payload: ByteArray): PairChallenge {
            val buf = ByteBuffer.wrap(payload)
            val nameLen = buf.short.toInt() and 0xFFFF
            val nameBytes = ByteArray(nameLen)
            buf.get(nameBytes)
            val fpLen = buf.short.toInt() and 0xFFFF
            val fpBytes = ByteArray(fpLen)
            buf.get(fpBytes)
            return PairChallenge(
                String(nameBytes, StandardCharsets.UTF_8),
                String(fpBytes, StandardCharsets.UTF_8)
            )
        }
    }
}

data class PairConfirm(val confirmed: Boolean) : Message() {
    override val type = MessageType.PAIR_CONFIRM
    override fun encodePayload() = byteArrayOf(if (confirmed) 1 else 0)

    companion object {
        fun decode(payload: ByteArray) = PairConfirm(payload[0] == 1.toByte())
    }
}

data class PairComplete(val paired: Boolean) : Message() {
    override val type = MessageType.PAIR_COMPLETE
    override fun encodePayload() = byteArrayOf(if (paired) 1 else 0)

    companion object {
        fun decode(payload: ByteArray) = PairComplete(payload[0] == 1.toByte())
    }
}

// --- Session Messages ---

data class SessionRequest(
    val codec: String = "OPUS",
    val sampleRate: Int = 48000,
    val channels: Int = 1,
    val bitrate: Int = 64000,
    val frameSizeMs: Int = 20
) : Message() {
    override val type = MessageType.SESSION_REQUEST
    override fun encodePayload(): ByteArray {
        val codecBytes = codec.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(2 + codecBytes.size + 4 * 3 + 4)
        buf.putShort(codecBytes.size.toShort())
        buf.put(codecBytes)
        buf.putInt(sampleRate)
        buf.putInt(channels)
        buf.putInt(bitrate)
        buf.putInt(frameSizeMs)
        return buf.array()
    }

    companion object {
        fun decode(payload: ByteArray): SessionRequest {
            val buf = ByteBuffer.wrap(payload)
            val codecLen = buf.short.toInt() and 0xFFFF
            val codecBytes = ByteArray(codecLen)
            buf.get(codecBytes)
            return SessionRequest(
                codec = String(codecBytes, StandardCharsets.UTF_8),
                sampleRate = buf.int,
                channels = buf.int,
                bitrate = buf.int,
                frameSizeMs = buf.int
            )
        }
    }
}

data class SessionAccept(
    val codec: String = "OPUS",
    val sampleRate: Int = 48000,
    val channels: Int = 1,
    val bitrate: Int = 64000,
    val frameSizeMs: Int = 20
) : Message() {
    override val type = MessageType.SESSION_ACCEPT
    override fun encodePayload(): ByteArray {
        val codecBytes = codec.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(2 + codecBytes.size + 4 * 3 + 4)
        buf.putShort(codecBytes.size.toShort())
        buf.put(codecBytes)
        buf.putInt(sampleRate)
        buf.putInt(channels)
        buf.putInt(bitrate)
        buf.putInt(frameSizeMs)
        return buf.array()
    }

    companion object {
        fun decode(payload: ByteArray): SessionAccept {
            val buf = ByteBuffer.wrap(payload)
            val codecLen = buf.short.toInt() and 0xFFFF
            val codecBytes = ByteArray(codecLen)
            buf.get(codecBytes)
            return SessionAccept(
                codec = String(codecBytes, StandardCharsets.UTF_8),
                sampleRate = buf.int,
                channels = buf.int,
                bitrate = buf.int,
                frameSizeMs = buf.int
            )
        }
    }
}

data class SessionReject(val reason: String) : Message() {
    override val type = MessageType.SESSION_REJECT
    override fun encodePayload(): ByteArray {
        val bytes = reason.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(2 + bytes.size)
        buf.putShort(bytes.size.toShort())
        buf.put(bytes)
        return buf.array()
    }

    companion object {
        fun decode(payload: ByteArray): SessionReject {
            val buf = ByteBuffer.wrap(payload)
            val len = buf.short.toInt() and 0xFFFF
            val bytes = ByteArray(len)
            buf.get(bytes)
            return SessionReject(String(bytes, StandardCharsets.UTF_8))
        }
    }
}

object SessionEnd : Message() {
    override val type = MessageType.SESSION_END
    override fun encodePayload() = ByteArray(0)
}

// --- Audio Messages ---

data class AudioFrame(
    val sequenceNumber: Long,
    val timestampMs: Long,
    val opusData: ByteArray
) : Message() {
    override val type = MessageType.AUDIO_FRAME
    override fun encodePayload(): ByteArray {
        val buf = ByteBuffer.allocate(4 + 8 + 2 + opusData.size)
        buf.putInt(sequenceNumber.toInt())
        buf.putLong(timestampMs)
        buf.putShort(opusData.size.toShort())
        buf.put(opusData)
        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return sequenceNumber == other.sequenceNumber &&
                timestampMs == other.timestampMs &&
                opusData.contentEquals(other.opusData)
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + opusData.contentHashCode()
        return result
    }

    companion object {
        fun decode(payload: ByteArray): AudioFrame {
            val buf = ByteBuffer.wrap(payload)
            val seq = buf.int.toLong() and 0xFFFFFFFFL
            val ts = buf.long
            val len = buf.short.toInt() and 0xFFFF
            val data = ByteArray(len)
            buf.get(data)
            return AudioFrame(seq, ts, data)
        }
    }
}

data class AudioConfig(
    val codec: String,
    val sampleRate: Int,
    val channels: Int,
    val bitrate: Int,
    val frameSizeMs: Int
) : Message() {
    override val type = MessageType.AUDIO_CONFIG
    override fun encodePayload(): ByteArray {
        val codecBytes = codec.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(2 + codecBytes.size + 16)
        buf.putShort(codecBytes.size.toShort())
        buf.put(codecBytes)
        buf.putInt(sampleRate)
        buf.putInt(channels)
        buf.putInt(bitrate)
        buf.putInt(frameSizeMs)
        return buf.array()
    }

    companion object {
        fun decode(payload: ByteArray): AudioConfig {
            val buf = ByteBuffer.wrap(payload)
            val codecLen = buf.short.toInt() and 0xFFFF
            val codecBytes = ByteArray(codecLen)
            buf.get(codecBytes)
            return AudioConfig(
                codec = String(codecBytes, StandardCharsets.UTF_8),
                sampleRate = buf.int,
                channels = buf.int,
                bitrate = buf.int,
                frameSizeMs = buf.int
            )
        }
    }
}

// --- Keepalive ---

object Keepalive : Message() {
    override val type = MessageType.KEEPALIVE
    override fun encodePayload() = ByteArray(0)
}

object KeepaliveAck : Message() {
    override val type = MessageType.KEEPALIVE_ACK
    override fun encodePayload() = ByteArray(0)
}

// --- Status ---

data class StatusUpdate(
    val state: String,
    val latencyMs: Int = 0,
    val packetLoss: Float = 0f,
    val extra: String = ""
) : Message() {
    override val type = MessageType.STATUS_UPDATE
    override fun encodePayload(): ByteArray {
        val stateBytes = state.toByteArray(StandardCharsets.UTF_8)
        val extraBytes = extra.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(2 + stateBytes.size + 4 + 4 + 2 + extraBytes.size)
        buf.putShort(stateBytes.size.toShort())
        buf.put(stateBytes)
        buf.putInt(latencyMs)
        buf.putFloat(packetLoss)
        buf.putShort(extraBytes.size.toShort())
        buf.put(extraBytes)
        return buf.array()
    }

    companion object {
        fun decode(payload: ByteArray): StatusUpdate {
            val buf = ByteBuffer.wrap(payload)
            val stateLen = buf.short.toInt() and 0xFFFF
            val stateBytes = ByteArray(stateLen)
            buf.get(stateBytes)
            val latency = buf.int
            val loss = buf.float
            val extraLen = buf.short.toInt() and 0xFFFF
            val extraBytes = ByteArray(extraLen)
            buf.get(extraBytes)
            return StatusUpdate(
                String(stateBytes, StandardCharsets.UTF_8),
                latency, loss,
                String(extraBytes, StandardCharsets.UTF_8)
            )
        }
    }
}
