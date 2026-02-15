package com.conspeak.android.ui.screens

import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conspeak.android.data.AudioSettings
import com.conspeak.android.data.SettingsStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(context) }
    val settings by store.settings.collectAsState(initial = AudioSettings())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Presets
            Text("Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !settings.lowPowerMode && settings.bitrate == 64000,
                    onClick = {
                        scope.launch {
                            store.update {
                                AudioSettings(
                                    sampleRate = 48000, bitrate = 64000,
                                    frameSizeMs = 20, lowPowerMode = false
                                )
                            }
                        }
                    },
                    label = { Text("Balanced") }
                )
                FilterChip(
                    selected = settings.lowPowerMode,
                    onClick = {
                        scope.launch {
                            store.update {
                                AudioSettings(
                                    sampleRate = 16000, bitrate = 24000,
                                    frameSizeMs = 40, lowPowerMode = true
                                )
                            }
                        }
                    },
                    label = { Text("Low Power") }
                )
                FilterChip(
                    selected = !settings.lowPowerMode && settings.bitrate == 128000,
                    onClick = {
                        scope.launch {
                            store.update {
                                AudioSettings(
                                    sampleRate = 48000, bitrate = 128000,
                                    frameSizeMs = 10, lowPowerMode = false
                                )
                            }
                        }
                    },
                    label = { Text("High Quality") }
                )
            }

            Divider()

            // Audio source
            Text("Microphone Source", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val sources = listOf(
                0 to "Default",
                1 to "MIC",
                2 to "VOICE_RECOGNITION",
                3 to "VOICE_COMMUNICATION"
            )
            sources.forEach { (value, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = settings.micSource == value,
                        onClick = {
                            scope.launch { store.update { it.copy(micSource = value) } }
                        }
                    )
                    Column {
                        Text(label)
                        if (value == 2) {
                            Text(
                                "May disable platform effects",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Divider()

            // Audio effects
            Text("Audio Effects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SettingsSwitch(
                label = "Noise Suppression",
                subtitle = if (NoiseSuppressor.isAvailable()) "Available on this device" else "Not available",
                checked = settings.noiseSuppression,
                enabled = NoiseSuppressor.isAvailable(),
                onCheckedChange = {
                    scope.launch { store.update { s -> s.copy(noiseSuppression = it) } }
                }
            )
            SettingsSwitch(
                label = "Echo Cancellation",
                subtitle = if (AcousticEchoCanceler.isAvailable()) "Available" else "Not available",
                checked = settings.echoCancellation,
                enabled = AcousticEchoCanceler.isAvailable(),
                onCheckedChange = {
                    scope.launch { store.update { s -> s.copy(echoCancellation = it) } }
                }
            )
            SettingsSwitch(
                label = "Auto Gain Control",
                subtitle = if (AutomaticGainControl.isAvailable()) "Available" else "Not available",
                checked = settings.autoGainControl,
                enabled = AutomaticGainControl.isAvailable(),
                onCheckedChange = {
                    scope.launch { store.update { s -> s.copy(autoGainControl = it) } }
                }
            )

            Divider()

            // Gain slider
            Text("Gain: ${"%.1f".format(settings.gain)}x", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = settings.gain,
                onValueChange = { scope.launch { store.update { s -> s.copy(gain = it) } } },
                valueRange = 0.1f..4.0f,
                steps = 38
            )

            Divider()

            // Advanced codec settings
            Text("Codec Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Sample rate
            Text("Sample Rate: ${settings.sampleRate} Hz")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(16000, 24000, 48000).forEach { rate ->
                    FilterChip(
                        selected = settings.sampleRate == rate,
                        onClick = { scope.launch { store.update { it.copy(sampleRate = rate) } } },
                        label = { Text("${rate / 1000}k") }
                    )
                }
            }

            // Bitrate
            Text("Bitrate: ${settings.bitrate / 1000} kbps")
            Slider(
                value = settings.bitrate.toFloat(),
                onValueChange = {
                    scope.launch { store.update { s -> s.copy(bitrate = it.toInt()) } }
                },
                valueRange = 12000f..128000f,
                steps = 11
            )

            // Frame size
            Text("Frame Size: ${settings.frameSizeMs} ms")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 40, 60).forEach { ms ->
                    FilterChip(
                        selected = settings.frameSizeMs == ms,
                        onClick = { scope.launch { store.update { it.copy(frameSizeMs = ms) } } },
                        label = { Text("${ms}ms") }
                    )
                }
            }

            // Jitter buffer
            Text("Jitter Buffer: ${settings.jitterBufferMs} ms")
            Slider(
                value = settings.jitterBufferMs.toFloat(),
                onValueChange = {
                    scope.launch { store.update { s -> s.copy(jitterBufferMs = it.toInt()) } }
                },
                valueRange = 20f..300f,
                steps = 13
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
