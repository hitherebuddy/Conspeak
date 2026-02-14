package com.conspeak.desktop.data

import org.slf4j.LoggerFactory
import java.io.File
import java.util.Properties

/**
 * Simple file-based settings store for the desktop app.
 * Stores settings in %APPDATA%/Conspeak/settings.properties (Windows)
 * or ~/.config/conspeak/settings.properties (Linux/Mac).
 */
class DesktopSettingsStore {

    private val log = LoggerFactory.getLogger("Settings")

    data class Settings(
        val deviceName: String = defaultDeviceName(),
        val audioOutputDevice: String? = null,
        val jitterBufferMs: Int = 60,
        val autoStart: Boolean = true,
        val minimizeToTray: Boolean = true,
        val logLevel: String = "INFO"
    ) {
        companion object {
            fun defaultDeviceName(): String {
                return try {
                    "${System.getProperty("user.name")}'s PC"
                } catch (_: Exception) {
                    "Conspeak Desktop"
                }
            }
        }
    }

    private val configDir: File
    private val configFile: File
    private val trustedFile: File

    init {
        val appData = System.getenv("APPDATA")
        configDir = if (appData != null) {
            File(appData, "Conspeak")
        } else {
            File(System.getProperty("user.home"), ".config/conspeak")
        }
        configDir.mkdirs()
        configFile = File(configDir, "settings.properties")
        trustedFile = File(configDir, "trusted_devices.txt")
    }

    fun load(): Settings {
        if (!configFile.exists()) return Settings()

        return try {
            val props = Properties()
            configFile.inputStream().use { props.load(it) }
            Settings(
                deviceName = props.getProperty("device_name", Settings.defaultDeviceName()),
                audioOutputDevice = props.getProperty("audio_output_device"),
                jitterBufferMs = props.getProperty("jitter_buffer_ms", "60").toIntOrNull() ?: 60,
                autoStart = props.getProperty("auto_start", "true").toBoolean(),
                minimizeToTray = props.getProperty("minimize_to_tray", "true").toBoolean(),
                logLevel = props.getProperty("log_level", "INFO")
            )
        } catch (e: Exception) {
            log.error("Error loading settings", e)
            Settings()
        }
    }

    fun save(settings: Settings) {
        try {
            val props = Properties()
            props.setProperty("device_name", settings.deviceName)
            settings.audioOutputDevice?.let { props.setProperty("audio_output_device", it) }
            props.setProperty("jitter_buffer_ms", settings.jitterBufferMs.toString())
            props.setProperty("auto_start", settings.autoStart.toString())
            props.setProperty("minimize_to_tray", settings.minimizeToTray.toString())
            props.setProperty("log_level", settings.logLevel)
            configFile.outputStream().use { props.store(it, "Conspeak Desktop Settings") }
        } catch (e: Exception) {
            log.error("Error saving settings", e)
        }
    }

    fun loadTrustedDevices(): Set<String> {
        if (!trustedFile.exists()) return emptySet()
        return try {
            trustedFile.readLines().filter { it.isNotBlank() }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    fun saveTrustedDevice(fingerprint: String) {
        try {
            trustedFile.appendText("$fingerprint\n")
        } catch (e: Exception) {
            log.error("Error saving trusted device", e)
        }
    }

    fun getLogDir(): File = File(configDir, "logs").also { it.mkdirs() }
}
