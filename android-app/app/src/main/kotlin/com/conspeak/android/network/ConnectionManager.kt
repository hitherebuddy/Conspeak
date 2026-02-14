package com.conspeak.android.network

import android.util.Log
import com.conspeak.android.data.AudioSettings
import com.conspeak.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * Manages the connection lifecycle to a Conspeak desktop instance.
 * Handles pairing, session negotiation, audio streaming, and keepalive.
 */
class ConnectionManager {

    companion object {
        private const val TAG = "ConnectionManager"
        private const val CONNECT_TIMEOUT_MS = 10_000
    }

    enum class State {
        DISCONNECTED,
        CONNECTING,
        PAIRING,
        PAIRED,
        STREAMING,
        RECONNECTING
    }

    private var socket: SSLSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readerJob: Job? = null
    private var keepaliveJob: Job? = null
    private var reconnectJob: Job? = null
    private var scope: CoroutineScope? = null

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode

    private val _latencyMs = MutableStateFlow(0)
    val latencyMs: StateFlow<Int> = _latencyMs

    private val _peerName = MutableStateFlow<String?>(null)
    val peerName: StateFlow<String?> = _peerName

    var localCertFingerprint: String = ""
        private set
    var peerCertFingerprint: String = ""
        private set

    // Callbacks
    var onSessionAccepted: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onPairingRequired: ((String) -> Unit)? = null // code

    private var targetHost: String = ""
    private var targetPort: Int = Constants.DEFAULT_PORT
    private var autoReconnect = true
    private var peerFingerprintTrusted: String? = null // for auto-reconnect

    /**
     * Connect to a desktop. If fingerprint is provided, skip pairing (already trusted).
     */
    fun connect(
        host: String,
        port: Int,
        trustedFingerprint: String? = null,
        coroutineScope: CoroutineScope
    ) {
        scope = coroutineScope
        targetHost = host
        targetPort = port
        peerFingerprintTrusted = trustedFingerprint

        coroutineScope.launch(Dispatchers.IO) {
            doConnect()
        }
    }

    private suspend fun doConnect() {
        _state.value = State.CONNECTING
        try {
            val (sslContext, fingerprint) = CryptoUtils.createSelfSignedSslContext()
            localCertFingerprint = fingerprint

            val factory = sslContext.socketFactory
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(targetHost, targetPort), CONNECT_TIMEOUT_MS)

            socket = factory.createSocket(rawSocket, targetHost, targetPort, true) as SSLSocket
            socket!!.apply {
                enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
                startHandshake()
            }

            inputStream = socket!!.inputStream
            outputStream = socket!!.outputStream

            // Get peer cert fingerprint
            val peerCerts = socket!!.session.peerCertificates
            if (peerCerts.isNotEmpty()) {
                val cert = peerCerts[0] as java.security.cert.X509Certificate
                peerCertFingerprint = CryptoUtils.getCertFingerprint(cert)
            }

            // Check if already trusted (auto-reconnect)
            if (peerFingerprintTrusted != null && peerCertFingerprint == peerFingerprintTrusted) {
                Log.d(TAG, "Auto-reconnecting to trusted peer")
                _state.value = State.PAIRED
                startSession()
            } else {
                // Need pairing
                initiatePairing()
            }

            startReader()
            startKeepalive()
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _state.value = State.DISCONNECTED
            if (autoReconnect && peerFingerprintTrusted != null) {
                scheduleReconnect()
            }
        }
    }

    private suspend fun initiatePairing() {
        _state.value = State.PAIRING

        val phoneName = android.os.Build.MODEL
        val request = PairRequest(phoneName, localCertFingerprint)
        sendMessage(request)

        // Code will be computed when we receive PairChallenge
    }

    fun confirmPairing() {
        scope?.launch(Dispatchers.IO) {
            sendMessage(PairConfirm(true))
        }
    }

    private fun startSession() {
        scope?.launch(Dispatchers.IO) {
            val request = SessionRequest()
            sendMessage(request)
        }
    }

    /**
     * Send an encoded audio frame.
     */
    fun sendAudioFrame(sequenceNumber: Long, timestampMs: Long, opusData: ByteArray) {
        scope?.launch(Dispatchers.IO) {
            val frame = AudioFrame(sequenceNumber, timestampMs, opusData)
            sendMessage(frame)
        }
    }

    private fun startReader() {
        readerJob = scope?.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val msg = FrameCodec.readMessage(inputStream!!) ?: continue
                    handleMessage(msg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reader error", e)
                handleDisconnect()
            }
        }
    }

    private fun handleMessage(msg: Message) {
        when (msg) {
            is PairChallenge -> {
                _peerName.value = msg.desktopName
                peerCertFingerprint = msg.desktopCertFingerprint
                val code = CryptoUtils.derivePairingCode(
                    localCertFingerprint,
                    peerCertFingerprint
                )
                _pairingCode.value = code
                onPairingRequired?.invoke(code)
            }
            is PairComplete -> {
                if (msg.paired) {
                    _state.value = State.PAIRED
                    peerFingerprintTrusted = peerCertFingerprint
                    startSession()
                }
            }
            is SessionAccept -> {
                _state.value = State.STREAMING
                onSessionAccepted?.invoke()
            }
            is SessionReject -> {
                Log.w(TAG, "Session rejected: ${msg.reason}")
            }
            is SessionEnd -> {
                _state.value = State.PAIRED
            }
            is KeepaliveAck -> {
                // Could measure RTT here
            }
            is Keepalive -> {
                scope?.launch(Dispatchers.IO) {
                    sendMessage(KeepaliveAck)
                }
            }
            is StatusUpdate -> {
                _latencyMs.value = msg.latencyMs
            }
            else -> {
                Log.d(TAG, "Unhandled message: ${msg.type}")
            }
        }
    }

    private fun startKeepalive() {
        keepaliveJob = scope?.launch(Dispatchers.IO) {
            while (isActive) {
                delay(Constants.KEEPALIVE_INTERVAL_MS)
                try {
                    sendMessage(Keepalive)
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    @Synchronized
    private fun sendMessage(msg: Message) {
        try {
            outputStream?.let { FrameCodec.writeMessage(it, msg) }
        } catch (e: Exception) {
            Log.e(TAG, "Send failed", e)
            handleDisconnect()
        }
    }

    private fun handleDisconnect() {
        val wasStreaming = _state.value == State.STREAMING
        _state.value = State.DISCONNECTED
        cleanup()
        onDisconnected?.invoke()

        if (autoReconnect && peerFingerprintTrusted != null) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope?.launch(Dispatchers.IO) {
            for (delay in Constants.RECONNECT_DELAYS_MS) {
                _state.value = State.RECONNECTING
                delay(delay)
                Log.d(TAG, "Reconnecting...")
                try {
                    doConnect()
                    if (_state.value == State.STREAMING || _state.value == State.PAIRED) {
                        return@launch // success
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt failed", e)
                }
            }
            _state.value = State.DISCONNECTED
        }
    }

    fun disconnect() {
        autoReconnect = false
        reconnectJob?.cancel()
        _state.value = State.DISCONNECTED
        scope?.launch(Dispatchers.IO) {
            try {
                sendMessage(SessionEnd)
            } catch (_: Exception) {}
            cleanup()
        }
    }

    private fun cleanup() {
        keepaliveJob?.cancel()
        readerJob?.cancel()
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
    }
}
