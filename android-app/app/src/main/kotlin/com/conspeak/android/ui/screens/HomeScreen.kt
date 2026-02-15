package com.conspeak.android.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conspeak.android.data.AudioSettings
import com.conspeak.android.data.PairingStore
import com.conspeak.android.data.SettingsStore
import com.conspeak.android.network.ConnectionManager
import com.conspeak.android.network.LanDiscovery
import com.conspeak.android.service.AudioStreamService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Stores
    val settingsStore = remember { SettingsStore(context) }
    val pairingStore = remember { PairingStore(context) }
    val settings by settingsStore.settings.collectAsState(initial = AudioSettings())

    // Discovery
    val discovery = remember { LanDiscovery(context) }
    val discoveredDesktops by discovery.desktops.collectAsState()

    // Service binding
    var service by remember { mutableStateOf<AudioStreamService?>(null) }
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as AudioStreamService.LocalBinder).getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
    }

    // State from service
    val connectionState = service?.connectionManager?.state?.collectAsState()
    val isStreaming = service?.isStreaming?.collectAsState()
    val audioLevel = service?.audioCaptureManager?.level?.collectAsState()
    val isMuted = service?.audioCaptureManager?.isMuted?.collectAsState()
    val pairingCode = service?.connectionManager?.pairingCode?.collectAsState()
    val peerName = service?.connectionManager?.peerName?.collectAsState()

    // USB mode state
    var usbMode by remember { mutableStateOf(false) }

    // Bind to service
    DisposableEffect(Unit) {
        val intent = Intent(context, AudioStreamService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        discovery.startDiscovery()

        onDispose {
            discovery.stopDiscovery()
            try { context.unbindService(serviceConnection) } catch (_: Exception) {}
        }
    }

    // Pairing dialog state
    var showPairingDialog by remember { mutableStateOf(false) }
    var currentPairingCode by remember { mutableStateOf("") }

    LaunchedEffect(pairingCode?.value) {
        pairingCode?.value?.let {
            currentPairingCode = it
            showPairingDialog = true
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Conspeak") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Text("...", fontSize = 20.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val state = connectionState?.value ?: ConnectionManager.State.DISCONNECTED
                    val statusColor = when (state) {
                        ConnectionManager.State.STREAMING -> Color(0xFF4CAF50)
                        ConnectionManager.State.PAIRED -> Color(0xFF2196F3)
                        ConnectionManager.State.CONNECTING,
                        ConnectionManager.State.PAIRING,
                        ConnectionManager.State.RECONNECTING -> Color(0xFFFFC107)
                        ConnectionManager.State.DISCONNECTED -> Color(0xFF757575)
                    }

                    // Status indicator
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (state) {
                            ConnectionManager.State.STREAMING -> "Streaming to ${peerName?.value ?: "Desktop"}"
                            ConnectionManager.State.PAIRED -> "Paired"
                            ConnectionManager.State.CONNECTING -> "Connecting..."
                            ConnectionManager.State.PAIRING -> "Pairing..."
                            ConnectionManager.State.RECONNECTING -> "Reconnecting..."
                            ConnectionManager.State.DISCONNECTED -> "Not Connected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Audio level meter
                    if (isStreaming?.value == true) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val level = audioLevel?.value ?: 0f
                        LinearProgressIndicator(
                            progress = { level },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (level > 0.9f) Color.Red else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Mute / Stop controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val muted = isMuted?.value ?: false
                            Button(
                                onClick = {
                                    service?.audioCaptureManager?.setMuted(!muted)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (muted) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(if (muted) "Unmute" else "Mute")
                            }

                            OutlinedButton(
                                onClick = { service?.stopStreaming() }
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // USB Mode Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (usbMode) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "USB Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (usbMode) "Connect via USB cable (127.0.0.1)"
                            else "Connect via Wi-Fi (mDNS discovery)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = usbMode,
                        onCheckedChange = { usbMode = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (usbMode) {
                // USB Mode: Direct connection to 127.0.0.1
                Text(
                    text = "USB Connection",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Make sure:",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("1. Phone connected via USB cable", style = MaterialTheme.typography.bodySmall)
                        Text("2. USB debugging enabled", style = MaterialTheme.typography.bodySmall)
                        Text("3. Desktop ran 'Setup USB' in ADB section", style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val intent = Intent(context, AudioStreamService::class.java)
                                context.startForegroundService(intent)
                                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

                                scope.launch {
                                    var attempts = 0
                                    while (service == null && attempts < 50) {
                                        delay(100)
                                        attempts++
                                    }

                                    service?.startStreaming(
                                        settings = settings,
                                        host = "127.0.0.1",  // Localhost via ADB reverse
                                        port = com.conspeak.protocol.Constants.DEFAULT_PORT,
                                        trustedFp = null  // Will need to pair first time
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect via USB")
                        }
                    }
                }
            } else {
                // Wi-Fi Mode: mDNS discovery (existing code)
                Text(
                    text = "Available Desktops",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

            if (discoveredDesktops.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Searching for desktops...")
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Make sure Conspeak Desktop is running on the same Wi-Fi network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredDesktops, key = { it.name }) { desktop ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val intent = Intent(context, AudioStreamService::class.java)
                                context.startForegroundService(intent)
                                // Re-bind after starting
                                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

                                scope.launch {
                                    // Check if already paired
                                    val trusted = if (pairingStore.isPaired(desktop.fingerprint)) {
                                        desktop.fingerprint
                                    } else null

                                    // Wait for service binding to complete before calling methods
                                    var attempts = 0
                                    while (service == null && attempts < 50) {
                                        delay(100)
                                        attempts++
                                    }

                                    service?.startStreaming(
                                        settings = settings,
                                        host = desktop.host,
                                        port = desktop.port,
                                        trustedFp = trusted
                                    )
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = desktop.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${desktop.host}:${desktop.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text("Connect", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            } // End of usbMode if/else
        }
    }

    // Pairing dialog
    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            title = { Text("Verify Pairing Code") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Confirm this code matches the one shown on your desktop:")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = currentPairingCode,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showPairingDialog = false
                    service?.connectionManager?.confirmPairing()
                    scope.launch {
                        service?.connectionManager?.let { cm ->
                            pairingStore.addPairedDevice(
                                PairingStore.PairedDevice(
                                    name = cm.peerName.value ?: "Desktop",
                                    certFingerprint = cm.peerCertFingerprint,
                                    lastIp = "",
                                    lastPort = com.conspeak.protocol.Constants.DEFAULT_PORT
                                )
                            )
                        }
                    }
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPairingDialog = false
                    service?.connectionManager?.disconnect()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
