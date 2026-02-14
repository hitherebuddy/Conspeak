package com.conspeak.protocol

object Constants {
    const val DEFAULT_PORT = 29170
    const val SERVICE_TYPE = "_conspeak._tcp.local."
    const val PROTOCOL_VERSION = 1

    // Keepalive
    const val KEEPALIVE_INTERVAL_MS = 5_000L
    const val KEEPALIVE_TIMEOUT_MS = 15_000L

    // Reconnect backoff
    val RECONNECT_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)

    // Audio defaults
    const val DEFAULT_SAMPLE_RATE = 48000
    const val DEFAULT_CHANNELS = 1
    const val DEFAULT_BITRATE = 64000
    const val DEFAULT_FRAME_SIZE_MS = 20
    const val DEFAULT_JITTER_BUFFER_MS = 60

    // Low power preset
    const val LOW_POWER_SAMPLE_RATE = 16000
    const val LOW_POWER_BITRATE = 24000
    const val LOW_POWER_FRAME_SIZE_MS = 40

    // High quality preset
    const val HQ_SAMPLE_RATE = 48000
    const val HQ_BITRATE = 128000
    const val HQ_FRAME_SIZE_MS = 10
}
