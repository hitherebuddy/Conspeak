package com.conspeak.desktop.network

import com.conspeak.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * TLS server that accepts connections from Conspeak Android clients.
 * Handles pairing, session negotiation, and audio frame reception.
 */
class DesktopServer(
    private val port: Int = Constants.DEFAULT_PORT
) {
    private val log = LoggerFactory.getLogger("DesktopServer")

    enum class State {
        STOPPED, LISTENING, PAIRING, CONNECTED, STREAMING
    }

    private var serverSocket: SSLServerSocket? = null
    private var clientSocket: SSLSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var serverJob: Job? = null
    private var readerJob: Job? = null
    private var keepaliveJob: Job? = null
    private var scope: CoroutineScope? = null

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode

    private val _clientName = MutableStateFlow<String?>(null)
    val clientName: StateFlow<String?> = _clientName

    private val _stats = MutableStateFlow(StreamStats())
    val stats: StateFlow<StreamStats> = _stats

    var localCertFingerprint: String = ""
        private set
    var clientCertFingerprint: String = ""
        private set

    // Audio frame callback
    var onAudioFrame: ((AudioFrame) -> Unit)? = null

    // Trusted client fingerprints (for auto-pair)
    private val trustedClients = mutableSetOf<String>()

    data class StreamStats(
        val framesReceived: Long = 0,
        val bytesReceived: Long = 0,
        val lastSequence: Long = -1,
        val gaps: Long = 0
    )

    fun start(coroutineScope: CoroutineScope) {
        scope = coroutineScope

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val (sslContext, fingerprint) = CryptoUtils.createSelfSignedSslContext()
                localCertFingerprint = fingerprint
                log.info("Server cert fingerprint: ${fingerprint.take(16)}...")

                serverSocket = sslContext.serverSocketFactory
                    .createServerSocket(port) as SSLServerSocket
                serverSocket!!.apply {
                    enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
                    needClientAuth = false // we verify via pairing
                    wantClientAuth = true
                }

                _state.value = State.LISTENING
                log.info("Listening on port $port")

                while (isActive) {
                    val socket = serverSocket!!.accept() as SSLSocket
                    log.info("Client connected from ${socket.remoteSocketAddress}")
                    handleClient(socket)
                }
            } catch (e: Exception) {
                if (isActive) {
                    log.error("Server error", e)
                }
                _state.value = State.STOPPED
            }
        }
    }

    private fun handleClient(socket: SSLSocket) {
        // Close previous client if any
        closeClient()

        clientSocket = socket
        inputStream = socket.inputStream
        outputStream = socket.outputStream

        // Get client cert fingerprint if available
        try {
            val peerCerts = socket.session.peerCertificates
            if (peerCerts.isNotEmpty()) {
                val cert = peerCerts[0] as java.security.cert.X509Certificate
                clientCertFingerprint = CryptoUtils.getCertFingerprint(cert)
            }
        } catch (_: Exception) {
            // Client may not have sent cert
        }

        _state.value = State.CONNECTED
        startReader()
        startKeepalive()
    }

    private fun startReader() {
        readerJob = scope?.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val msg = FrameCodec.readMessage(inputStream!!) ?: continue
                    handleMessage(msg)
                }
            } catch (e: Exception) {
                log.error("Client read error", e)
                _state.value = State.LISTENING
                closeClient()
            }
        }
    }

    private fun handleMessage(msg: Message) {
        when (msg) {
            is PairRequest -> {
                _clientName.value = msg.phoneName
                clientCertFingerprint = msg.phoneCertFingerprint
                log.info("Pair request from: ${msg.phoneName}")

                // Check if already trusted
                if (trustedClients.contains(msg.phoneCertFingerprint)) {
                    log.info("Auto-pairing trusted client")
                    sendMessage(PairChallenge("Conspeak Desktop", localCertFingerprint))
                    sendMessage(PairComplete(true))
                    _state.value = State.CONNECTED
                    return
                }

                // Send challenge and show pairing code
                sendMessage(PairChallenge("Conspeak Desktop", localCertFingerprint))
                val code = CryptoUtils.derivePairingCode(
                    msg.phoneCertFingerprint,
                    localCertFingerprint
                )
                _pairingCode.value = code
                _state.value = State.PAIRING
            }

            is PairConfirm -> {
                if (msg.confirmed) {
                    trustedClients.add(clientCertFingerprint)
                    sendMessage(PairComplete(true))
                    _pairingCode.value = null
                    _state.value = State.CONNECTED
                    log.info("Pairing complete, client trusted")
                }
            }

            is SessionRequest -> {
                log.info("Session request: ${msg.codec} ${msg.sampleRate}Hz ${msg.bitrate}bps ${msg.frameSizeMs}ms")
                // Accept with same params
                val accept = SessionAccept(
                    codec = msg.codec,
                    sampleRate = msg.sampleRate,
                    channels = msg.channels,
                    bitrate = msg.bitrate,
                    frameSizeMs = msg.frameSizeMs
                )
                sendMessage(accept)
                _state.value = State.STREAMING
                _stats.value = StreamStats()
            }

            is AudioFrame -> {
                val prev = _stats.value
                val gaps = if (prev.lastSequence >= 0 && msg.sequenceNumber > prev.lastSequence + 1) {
                    prev.gaps + (msg.sequenceNumber - prev.lastSequence - 1)
                } else prev.gaps

                _stats.value = prev.copy(
                    framesReceived = prev.framesReceived + 1,
                    bytesReceived = prev.bytesReceived + msg.opusData.size,
                    lastSequence = msg.sequenceNumber,
                    gaps = gaps
                )
                onAudioFrame?.invoke(msg)
            }

            is SessionEnd -> {
                _state.value = State.CONNECTED
                log.info("Session ended by client")
            }

            is Keepalive -> {
                sendMessage(KeepaliveAck)
            }

            is KeepaliveAck -> { /* ok */ }

            is StatusUpdate -> {
                log.debug("Client status: ${msg.state}")
            }

            else -> {
                log.debug("Unhandled message: ${msg.type}")
            }
        }
    }

    private fun startKeepalive() {
        keepaliveJob = scope?.launch(Dispatchers.IO) {
            while (isActive) {
                delay(Constants.KEEPALIVE_INTERVAL_MS)
                try {
                    sendMessage(Keepalive)
                } catch (_: Exception) {
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
            log.error("Send failed", e)
        }
    }

    fun confirmPairing() {
        // Desktop side: user confirmed code matches
        // Already handled in PairConfirm from phone
    }

    fun rejectPairing() {
        _pairingCode.value = null
        _state.value = State.LISTENING
        closeClient()
    }

    fun addTrustedClient(fingerprint: String) {
        trustedClients.add(fingerprint)
    }

    fun getTrustedClients(): Set<String> = trustedClients.toSet()

    private fun closeClient() {
        readerJob?.cancel()
        keepaliveJob?.cancel()
        try { clientSocket?.close() } catch (_: Exception) {}
        clientSocket = null
        inputStream = null
        outputStream = null
    }

    fun stop() {
        _state.value = State.STOPPED
        closeClient()
        serverJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
