package com.conspeak.desktop.network

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Network utilities for finding the best local IP address to advertise.
 */
object NetworkUtils {

    /**
     * Get the best LAN IPv4 address for advertising via mDNS.
     * Prioritizes private IPv4 addresses on up interfaces.
     * Falls back to InetAddress.getLocalHost() if no suitable interface found.
     */
    fun getBestLocalAddress(): InetAddress {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()

            // Prefer private IPv4 addresses (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
            val candidates = interfaces
                .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }

            // Prioritize private addresses
            val privateAddr = candidates.firstOrNull { isPrivateAddress(it) }
            if (privateAddr != null) {
                return privateAddr
            }

            // Fall back to any non-loopback IPv4
            val publicAddr = candidates.firstOrNull()
            if (publicAddr != null) {
                return publicAddr
            }
        } catch (e: Exception) {
            // Fall through to default
        }

        // Last resort: use getLocalHost()
        return InetAddress.getLocalHost()
    }

    /**
     * Check if an IPv4 address is in a private range.
     */
    private fun isPrivateAddress(addr: Inet4Address): Boolean {
        val bytes = addr.address
        return when (bytes[0].toInt() and 0xFF) {
            10 -> true  // 10.0.0.0/8
            172 -> (bytes[1].toInt() and 0xFF) in 16..31  // 172.16.0.0/12
            192 -> (bytes[1].toInt() and 0xFF) == 168     // 192.168.0.0/16
            else -> false
        }
    }

    /**
     * Get all local IPv4 addresses for diagnostic purposes.
     */
    fun getAllLocalAddresses(): List<String> {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp }
                .flatMap { iface ->
                    iface.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .map { "${iface.displayName}: ${it.hostAddress}" }
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
