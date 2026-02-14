package com.conspeak.protocol

/**
 * All message types in the Conspeak protocol.
 */
enum class MessageType(val code: Byte) {
    // Pairing
    PAIR_REQUEST(0x01),
    PAIR_CHALLENGE(0x02),
    PAIR_CONFIRM(0x03),
    PAIR_COMPLETE(0x04),

    // Session
    SESSION_REQUEST(0x10),
    SESSION_ACCEPT(0x11),
    SESSION_REJECT(0x12),
    SESSION_END(0x13),

    // Audio
    AUDIO_FRAME(0x20),
    AUDIO_CONFIG(0x21),

    // Keepalive
    KEEPALIVE(0x30),
    KEEPALIVE_ACK(0x31),

    // Status
    STATUS_UPDATE(0x40);

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Byte): MessageType? = byCode[code]
    }
}
