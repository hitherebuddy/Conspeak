package com.conspeak.desktop.network

import com.conspeak.protocol.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory

/**
 * Manages ADB-connected devices for USB transport mode.
 * Detects connected devices, sets up port forwarding, and monitors connection.
 */
class AdbManager {

    private val log = LoggerFactory.getLogger("AdbManager")

    data class AdbDevice(
        val serial: String,
        val model: String,
        val state: String,  // "device", "unauthorized", "offline"
        val isForwarded: Boolean = false
    )

    private val _devices = MutableStateFlow<List<AdbDevice>>(emptyList())
    val devices: StateFlow<List<AdbDevice>> = _devices

    private val _adbAvailable = MutableStateFlow(false)
    val adbAvailable: StateFlow<Boolean> = _adbAvailable

    private var scanJob: Job? = null

    /**
     * Start periodic scanning for ADB devices.
     */
    fun startScanning(scope: CoroutineScope, intervalMs: Long = 3000) {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            // Check if adb is available
            _adbAvailable.value = isAdbInstalled()
            if (!_adbAvailable.value) {
                log.warn("ADB not found in PATH")
                return@launch
            }

            while (isActive) {
                scanDevices()
                delay(intervalMs)
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
    }

    private fun isAdbInstalled(): Boolean {
        return try {
            val process = ProcessBuilder("adb", "version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("Android Debug Bridge")
        } catch (_: Exception) {
            false
        }
    }

    private fun scanDevices() {
        try {
            val process = ProcessBuilder("adb", "devices", "-l")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val devices = mutableListOf<AdbDevice>()
            output.lines().drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val serial = parts[0]
                    val state = parts[1]
                    val model = parts.find { it.startsWith("model:") }
                        ?.removePrefix("model:") ?: serial
                    devices.add(AdbDevice(serial, model, state))
                }
            }

            _devices.value = devices
        } catch (e: Exception) {
            log.error("Error scanning ADB devices", e)
        }
    }

    /**
     * Set up ADB reverse port forwarding so the Android app on the device
     * can connect to localhost:PORT which tunnels to the desktop's PORT.
     */
    fun setupReverse(serial: String, port: Int = Constants.DEFAULT_PORT): Boolean {
        return try {
            val process = ProcessBuilder(
                "adb", "-s", serial,
                "reverse", "tcp:$port", "tcp:$port"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                log.info("ADB reverse set up for $serial: tcp:$port -> tcp:$port")
                // Update device state
                _devices.value = _devices.value.map {
                    if (it.serial == serial) it.copy(isForwarded = true) else it
                }
                true
            } else {
                log.error("ADB reverse failed: $output")
                false
            }
        } catch (e: Exception) {
            log.error("Error setting up ADB reverse", e)
            false
        }
    }

    /**
     * Remove ADB reverse forwarding.
     */
    fun removeReverse(serial: String, port: Int = Constants.DEFAULT_PORT): Boolean {
        return try {
            val process = ProcessBuilder(
                "adb", "-s", serial,
                "reverse", "--remove", "tcp:$port"
            ).redirectErrorStream(true).start()
            process.waitFor()
            _devices.value = _devices.value.map {
                if (it.serial == serial) it.copy(isForwarded = false) else it
            }
            true
        } catch (e: Exception) {
            log.error("Error removing ADB reverse", e)
            false
        }
    }
}
