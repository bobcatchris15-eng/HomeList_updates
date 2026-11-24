package com.example.mesh

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class DetectedAddresses(
    val lanIp: String = ""
)

object NetworkAddressDetector {
    /**
     * Inspects local device interfaces to find the Home Wi-Fi LAN IPv4 address.
     */
    fun detect(): DetectedAddresses {
        var lanIp = ""
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val ifaceName = iface.name.lowercase()
                val addrs = Collections.list(iface.inetAddresses)
                for (addr in addrs) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    val bytes = addr.address
                    val b0 = bytes[0].toInt() and 0xFF
                    val b1 = bytes[1].toInt() and 0xFF

                    if (isPrivateLanBytes(b0, b1)) {
                        // Prefer wlan/wifi interfaces over cellular rmnet
                        if (lanIp.isEmpty() || ifaceName.startsWith("wlan") || ifaceName.startsWith("eth")) {
                            lanIp = host
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Gracefully fall back to empty address when interfaces cannot be queried
        }
        return DetectedAddresses(lanIp = lanIp)
    }

    fun isLanAddress(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val clean = host.trim().removePrefix("/")
        if (clean == "127.0.0.1" || clean == "localhost") return true
        val parts = clean.split(".")
        if (parts.size != 4) return false
        val b0 = parts[0].toIntOrNull() ?: return false
        val b1 = parts[1].toIntOrNull() ?: return false
        return isPrivateLanBytes(b0, b1)
    }

    private fun isPrivateLanBytes(b0: Int, b1: Int): Boolean {
        return b0 == 192 && b1 == 168 ||
            b0 == 10 ||
            (b0 == 172 && b1 in 16..31)
    }
}
