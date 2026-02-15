package com.conspeak.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.pairingDataStore: DataStore<Preferences> by preferencesDataStore(name = "pairing")

/**
 * Persists pairing info: paired desktop name, cert fingerprint, last IP.
 * Each paired device is stored as a JSON string keyed by fingerprint.
 */
class PairingStore(private val context: Context) {

    private val pairedDevicesKey = stringSetPreferencesKey("paired_devices")

    data class PairedDevice(
        val name: String,
        val certFingerprint: String,
        val lastIp: String,
        val lastPort: Int
    ) {
        fun toJson(): String = JSONObject().apply {
            put("name", name)
            put("fp", certFingerprint)
            put("ip", lastIp)
            put("port", lastPort)
        }.toString()

        companion object {
            fun fromJson(json: String): PairedDevice {
                val obj = JSONObject(json)
                return PairedDevice(
                    name = obj.getString("name"),
                    certFingerprint = obj.getString("fp"),
                    lastIp = obj.getString("ip"),
                    lastPort = obj.getInt("port")
                )
            }
        }
    }

    suspend fun getPairedDevices(): List<PairedDevice> {
        val prefs = context.pairingDataStore.data.first()
        val set = prefs[pairedDevicesKey] ?: emptySet()
        return set.mapNotNull {
            try { PairedDevice.fromJson(it) } catch (_: Exception) { null }
        }
    }

    suspend fun addPairedDevice(device: PairedDevice) {
        context.pairingDataStore.edit { prefs ->
            val current = prefs[pairedDevicesKey]?.toMutableSet() ?: mutableSetOf()
            // Remove existing entry with same fingerprint
            current.removeAll { json ->
                try {
                    PairedDevice.fromJson(json).certFingerprint == device.certFingerprint
                } catch (_: Exception) { false }
            }
            current.add(device.toJson())
            prefs[pairedDevicesKey] = current
        }
    }

    suspend fun removePairedDevice(fingerprint: String) {
        context.pairingDataStore.edit { prefs ->
            val current = prefs[pairedDevicesKey]?.toMutableSet() ?: mutableSetOf()
            current.removeAll { json ->
                try {
                    PairedDevice.fromJson(json).certFingerprint == fingerprint
                } catch (_: Exception) { false }
            }
            prefs[pairedDevicesKey] = current
        }
    }

    suspend fun isPaired(fingerprint: String): Boolean {
        return getPairedDevices().any { it.certFingerprint == fingerprint }
    }
}
