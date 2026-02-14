package com.conspeak.desktop.network

import com.conspeak.protocol.Constants
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Advertises the Conspeak desktop service via mDNS so Android clients can discover it.
 */
class MdnsAdvertiser {

    private val log = LoggerFactory.getLogger("MdnsAdvertiser")
    private var jmdns: JmDNS? = null
    private var serviceInfo: ServiceInfo? = null

    fun start(
        deviceName: String,
        port: Int = Constants.DEFAULT_PORT,
        certFingerprint: String
    ) {
        try {
            val addr = InetAddress.getLocalHost()
            jmdns = JmDNS.create(addr, "conspeak")

            val txtMap = mapOf(
                "version" to Constants.PROTOCOL_VERSION.toString(),
                "name" to deviceName,
                "fingerprint" to certFingerprint.take(16)
            )

            serviceInfo = ServiceInfo.create(
                "_conspeak._tcp.local.",
                deviceName,
                port,
                0, 0,
                txtMap
            )

            jmdns?.registerService(serviceInfo)
            log.info("mDNS service registered: $deviceName on port $port (${addr.hostAddress})")
        } catch (e: Exception) {
            log.error("Failed to start mDNS advertiser", e)
        }
    }

    fun stop() {
        try {
            serviceInfo?.let { jmdns?.unregisterService(it) }
            jmdns?.close()
        } catch (e: Exception) {
            log.warn("Error stopping mDNS", e)
        }
        jmdns = null
        serviceInfo = null
    }

    fun getLocalAddress(): String {
        return try {
            InetAddress.getLocalHost().hostAddress ?: "unknown"
        } catch (_: Exception) { "unknown" }
    }
}
