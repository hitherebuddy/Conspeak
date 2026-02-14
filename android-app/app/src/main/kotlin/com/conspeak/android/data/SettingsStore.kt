package com.conspeak.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AudioSettings(
    val sampleRate: Int = 48000,
    val bitrate: Int = 64000,
    val frameSizeMs: Int = 20,
    val codec: String = "OPUS",
    val micSource: Int = 1, // MediaRecorder.AudioSource.MIC
    val noiseSuppression: Boolean = false,
    val echoCancellation: Boolean = false,
    val autoGainControl: Boolean = false,
    val gain: Float = 1.0f,
    val jitterBufferMs: Int = 60,
    val lowPowerMode: Boolean = false
)

class SettingsStore(private val context: Context) {

    private val sampleRateKey = intPreferencesKey("sample_rate")
    private val bitrateKey = intPreferencesKey("bitrate")
    private val frameSizeKey = intPreferencesKey("frame_size_ms")
    private val codecKey = stringPreferencesKey("codec")
    private val micSourceKey = intPreferencesKey("mic_source")
    private val nsKey = booleanPreferencesKey("noise_suppression")
    private val aecKey = booleanPreferencesKey("echo_cancellation")
    private val agcKey = booleanPreferencesKey("auto_gain_control")
    private val gainKey = floatPreferencesKey("gain")
    private val jitterKey = intPreferencesKey("jitter_buffer_ms")
    private val lowPowerKey = booleanPreferencesKey("low_power_mode")

    val settings: Flow<AudioSettings> = context.settingsDataStore.data.map { prefs ->
        AudioSettings(
            sampleRate = prefs[sampleRateKey] ?: 48000,
            bitrate = prefs[bitrateKey] ?: 64000,
            frameSizeMs = prefs[frameSizeKey] ?: 20,
            codec = prefs[codecKey] ?: "OPUS",
            micSource = prefs[micSourceKey] ?: 1,
            noiseSuppression = prefs[nsKey] ?: false,
            echoCancellation = prefs[aecKey] ?: false,
            autoGainControl = prefs[agcKey] ?: false,
            gain = prefs[gainKey] ?: 1.0f,
            jitterBufferMs = prefs[jitterKey] ?: 60,
            lowPowerMode = prefs[lowPowerKey] ?: false
        )
    }

    suspend fun update(transform: (AudioSettings) -> AudioSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = AudioSettings(
                sampleRate = prefs[sampleRateKey] ?: 48000,
                bitrate = prefs[bitrateKey] ?: 64000,
                frameSizeMs = prefs[frameSizeKey] ?: 20,
                codec = prefs[codecKey] ?: "OPUS",
                micSource = prefs[micSourceKey] ?: 1,
                noiseSuppression = prefs[nsKey] ?: false,
                echoCancellation = prefs[aecKey] ?: false,
                autoGainControl = prefs[agcKey] ?: false,
                gain = prefs[gainKey] ?: 1.0f,
                jitterBufferMs = prefs[jitterKey] ?: 60,
                lowPowerMode = prefs[lowPowerKey] ?: false
            )
            val updated = transform(current)
            prefs[sampleRateKey] = updated.sampleRate
            prefs[bitrateKey] = updated.bitrate
            prefs[frameSizeKey] = updated.frameSizeMs
            prefs[codecKey] = updated.codec
            prefs[micSourceKey] = updated.micSource
            prefs[nsKey] = updated.noiseSuppression
            prefs[aecKey] = updated.echoCancellation
            prefs[agcKey] = updated.autoGainControl
            prefs[gainKey] = updated.gain
            prefs[jitterKey] = updated.jitterBufferMs
            prefs[lowPowerKey] = updated.lowPowerMode
        }
    }
}
