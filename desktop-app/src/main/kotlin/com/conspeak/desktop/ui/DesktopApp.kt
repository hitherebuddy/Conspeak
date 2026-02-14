package com.conspeak.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conspeak.desktop.audio.AudioOutputManager
import com.conspeak.desktop.data.DesktopSettingsStore
import com.conspeak.desktop.network.AdbManager
import com.conspeak.desktop.network.DesktopServer
import com.conspeak.desktop.network.MdnsAdvertiser
import com.conspeak.protocol.Constants
import kotlinx.coroutines.launch

@Composable
fun DesktopApp() {
    val scope = rememberCoroutineScope()

    // Managers
    val settingsStore = remember { DesktopSettingsStore() }
    val settings = remember { mutableStateOf(settingsStore.load()) }
    val server = remember { DesktopServer() }
    val mdns = remember { MdnsAdvertiser() }
    val audioOutput = remember { AudioOutputManager() }
    val adbManager = remember { AdbManager() }

    // State
    val serverState by server.state.collectAsState()
    val pairingCode by server.pairingCode.collectAsState()
    val clientName by server.clientName.collectAsState()
    val serverStats by server.stats.collectAsState()
    val audioLevel by audioOutput.level.collectAsState()
    val isPlaying by audioOutput.isPlaying.collectAsState()
    val adbDevices by adbManager.devices.collectAsState()
    val adbAvailable by adbManager.adbAvailable.collectAsState()

    var showAdvanced by remember { mutableStateOf(false) }
    var selectedAudioDevice by remember { mutableStateOf(settings.value.audioOutputDevice) }
    val availableAudioDevices = remember { audioOutput.getAvailableDevices() }

    // Start server and mDNS on launch
    LaunchedEffect(Unit) {
        server.onAudioFrame = { frame ->
            audioOutput.feedFrame(frame)
        }

        // Load trusted devices
        settingsStore.loadTrustedDevices().forEach { server.addTrustedClient(it) }

        server.start(scope)
        // Wait for server cert to be ready
        kotlinx.coroutines.delay(500)
        mdns.start(settings.value.deviceName, Constants.DEFAULT_PORT, server.localCertFingerprint)
        adbManager.startScanning(scope)
    }

    // Auto-start audio output when streaming begins
    LaunchedEffect(serverState) {
        if (serverState == DesktopServer.State.STREAMING && !isPlaying) {
            val device = selectedAudioDevice ?: audioOutput.findVbCableDevice()
            selectedAudioDevice = device
            audioOutput.start(
                sampleRate = Constants.DEFAULT_SAMPLE_RATE,
                frameSizeMs = Constants.DEFAULT_FRAME_SIZE_MS,
                jitterBufferMs = settings.value.jitterBufferMs,
                deviceName = device,
                scope = scope
            )
        } else if (serverState != DesktopServer.State.STREAMING && isPlaying) {
            audioOutput.stop()
        }
    }

    // Save trusted client when pairing completes
    LaunchedEffect(serverState) {
        if (serverState == DesktopServer.State.CONNECTED || serverState == DesktopServer.State.STREAMING) {
            if (server.clientCertFingerprint.isNotEmpty()) {
                settingsStore.saveTrustedDevice(server.clientCertFingerprint)
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF90CAF9),
            secondary = Color(0xFF80CBC4),
            background = Color(0xFF1A1A2E),
            surface = Color(0xFF16213E),
            onBackground = Color(0xFFE0E0E0),
            onSurface = Color(0xFFE0E0E0)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    "Conspeak",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Phone as Microphone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Connection Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val statusColor = when (serverState) {
                            DesktopServer.State.STREAMING -> Color(0xFF4CAF50)
                            DesktopServer.State.CONNECTED -> Color(0xFF2196F3)
                            DesktopServer.State.PAIRING -> Color(0xFFFFC107)
                            DesktopServer.State.LISTENING -> Color(0xFF9E9E9E)
                            DesktopServer.State.STOPPED -> Color(0xFFF44336)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when (serverState) {
                                    DesktopServer.State.STREAMING -> "Streaming from ${clientName ?: "phone"}"
                                    DesktopServer.State.CONNECTED -> "Connected: ${clientName ?: "phone"}"
                                    DesktopServer.State.PAIRING -> "Pairing..."
                                    DesktopServer.State.LISTENING -> "Waiting for phone..."
                                    DesktopServer.State.STOPPED -> "Server stopped"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "IP: ${mdns.getLocalAddress()} | Port: ${Constants.DEFAULT_PORT}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        // Pairing code display
                        if (pairingCode != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF2D2D5E)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Pairing Code",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFFFFC107)
                                    )
                                    Text(
                                        pairingCode!!,
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 8.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        "Verify this matches the code on your phone",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        // Audio level meter (when streaming)
                        if (serverState == DesktopServer.State.STREAMING) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Audio Level", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { audioLevel },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                color = when {
                                    audioLevel > 0.9f -> Color.Red
                                    audioLevel > 0.7f -> Color(0xFFFFC107)
                                    else -> Color(0xFF4CAF50)
                                },
                                trackColor = Color(0xFF333366)
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem("Frames", serverStats.framesReceived.toString())
                                StatItem("Gaps", serverStats.gaps.toString())
                                StatItem("Data", "${serverStats.bytesReceived / 1024} KB")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { audioOutput.setMuted(!audioOutput.isPlaying.value) }) {
                                    Text("Mute")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Audio Output Device Selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Audio Output Device",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val vbCable = audioOutput.findVbCableDevice()
                        if (vbCable != null) {
                            Text(
                                "VB-CABLE detected",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "VB-CABLE not found. Install from vb-audio.com/Cable/",
                                color = Color(0xFFFFC107),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedAudioDevice ?: "Auto-detect",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Output Device") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Auto-detect") },
                                    onClick = {
                                        selectedAudioDevice = null
                                        expanded = false
                                    }
                                )
                                availableAudioDevices.forEach { device ->
                                    DropdownMenuItem(
                                        text = { Text(device) },
                                        onClick = {
                                            selectedAudioDevice = device
                                            settings.value = settings.value.copy(audioOutputDevice = device)
                                            settingsStore.save(settings.value)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // USB / ADB Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "USB Connection (ADB)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (!adbAvailable) {
                            Text(
                                "ADB not found in PATH. Install Android Platform Tools for USB support.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        } else if (adbDevices.isEmpty()) {
                            Text(
                                "No USB devices detected. Connect a phone with USB debugging enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        } else {
                            adbDevices.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(device.model, fontWeight = FontWeight.Bold)
                                        Text(
                                            when (device.state) {
                                                "device" -> if (device.isForwarded) "Forwarded" else "Ready"
                                                "unauthorized" -> "Unauthorized - check phone"
                                                else -> device.state
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (device.state) {
                                                "device" -> Color(0xFF4CAF50)
                                                "unauthorized" -> Color(0xFFFFC107)
                                                else -> Color(0xFFF44336)
                                            }
                                        )
                                    }
                                    if (device.state == "device" && !device.isForwarded) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    adbManager.setupReverse(device.serial)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF1565C0)
                                            )
                                        ) {
                                            Text("Setup USB")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Advanced Settings Toggle
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "Hide Advanced" else "Show Advanced")
                }

                if (showAdvanced) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Advanced Settings",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Device name
                            var deviceName by remember { mutableStateOf(settings.value.deviceName) }
                            OutlinedTextField(
                                value = deviceName,
                                onValueChange = { deviceName = it },
                                label = { Text("Device Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Jitter buffer
                            Text("Jitter Buffer: ${settings.value.jitterBufferMs} ms")
                            var jitterMs by remember { mutableStateOf(settings.value.jitterBufferMs.toFloat()) }
                            Slider(
                                value = jitterMs,
                                onValueChange = { jitterMs = it },
                                valueRange = 20f..300f,
                                steps = 13,
                                onValueChangeFinished = {
                                    settings.value = settings.value.copy(jitterBufferMs = jitterMs.toInt())
                                    settingsStore.save(settings.value)
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Save button
                            Button(
                                onClick = {
                                    settings.value = settings.value.copy(deviceName = deviceName)
                                    settingsStore.save(settings.value)
                                }
                            ) {
                                Text("Save Settings")
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Diagnostics
                            Text(
                                "Diagnostics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                buildString {
                                    appendLine("Server state: $serverState")
                                    appendLine("Cert FP: ${server.localCertFingerprint.take(16)}...")
                                    appendLine("Client FP: ${server.clientCertFingerprint.take(16)}...")
                                    appendLine("Jitter depth: ${audioOutput.getJitterBufferDepthMs()} ms")
                                    appendLine("Packet loss: ${"%.2f".format(audioOutput.getPacketLossRatio() * 100)}%")
                                    appendLine("Frames rx: ${serverStats.framesReceived}")
                                    appendLine("Sequence gaps: ${serverStats.gaps}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Instructions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Quick Start", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("1. Install VB-CABLE (vb-audio.com/Cable/)", style = MaterialTheme.typography.bodySmall)
                        Text("2. Open Conspeak on your Android phone", style = MaterialTheme.typography.bodySmall)
                        Text("3. Tap the desktop name to connect", style = MaterialTheme.typography.bodySmall)
                        Text("4. Verify pairing code on both devices", style = MaterialTheme.typography.bodySmall)
                        Text("5. In Discord/Zoom, select 'CABLE Output' as mic", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}
