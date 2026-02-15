package com.conspeak.android.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.conspeak.protocol.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Discovers Conspeak desktop instances on the LAN via mDNS (NsdManager).
 */
class LanDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "LanDiscovery"
        private const val SERVICE_TYPE = "_conspeak._tcp."
    }

    data class DiscoveredDesktop(
        val name: String,
        val host: String,
        val port: Int,
        val fingerprint: String,
        val version: Int
    )

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false

    private val _desktops = MutableStateFlow<List<DiscoveredDesktop>>(emptyList())
    val desktops: StateFlow<List<DiscoveredDesktop>> = _desktops

    fun startDiscovery() {
        if (isDiscovering) return

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Discovery started for $regType")
                isDiscovering = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                nsdManager?.resolveService(serviceInfo, createResolveListener())
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                _desktops.value = _desktops.value.filter { it.name != serviceInfo.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        if (!isDiscovering) return
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping discovery", e)
        }
        isDiscovering = false
        _desktops.value = emptyList()
    }

    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            // Extract TXT record attributes first
            val attrs = serviceInfo.attributes
            val fingerprint = attrs["fingerprint"]?.let { String(it) } ?: ""
            val version = attrs["version"]?.let { String(it).toIntOrNull() } ?: 1
            val explicitIp = attrs["ip"]?.let { String(it) }

            // Prefer explicit IP from TXT, fallback to host resolution
            val host = explicitIp ?: serviceInfo.host?.hostAddress

            if (host == null) {
                Log.w(TAG, "No valid host address for ${serviceInfo.serviceName}")
                return
            }

            val port = serviceInfo.port

            Log.d(TAG, "Resolved: ${serviceInfo.serviceName} at $host:$port fp=$fingerprint")
            Log.d(TAG, "  NSD host: ${serviceInfo.host?.hostAddress}, TXT ip: $explicitIp")

            val desktop = DiscoveredDesktop(
                name = serviceInfo.serviceName,
                host = host,
                port = port,
                fingerprint = fingerprint,
                version = version
            )

            _desktops.value = _desktops.value.filter { it.name != desktop.name } + desktop
        }
    }
}
