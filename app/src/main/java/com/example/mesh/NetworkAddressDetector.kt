package com.example.mesh

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class DetectedAddresses(
    val lanIp: String = "",
    val tailscaleIp: String = ""
)

object NetworkAddressDetector {
    /**
     * Inspects local device interfaces to find both the Home Wi-Fi LAN IPv4 address
     * and any Tailscale / WireGuard CGNAT virtual IPv4 address (100.64.0.0/10).
     */
    fun detect(): DetectedAddresses {
        var lanIp = ""
        var tailscaleIp = ""
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

                    // Tailscale uses CGNAT 100.64.0.0/10 (100.64.x.x - 100.127.x.x)
                    if (b0 == 100 && b1 in 64..127) {
                        if (tailscaleIp.isEmpty() || ifaceName.contains("tun") || ifaceName.contains("ts")) {
                            tailscaleIp = host
                        }
                    } else if (isPrivateLanBytes(b0, b1)) {
                        // Prefer wlan/wifi interfaces over cellular rmnet
                        if (lanIp.isEmpty() || ifaceName.startsWith("wlan") || ifaceName.startsWith("eth")) {
                            lanIp = host
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Gracefully fall back to empty addresses when interfaces cannot be queried
        }
        return DetectedAddresses(lanIp = lanIp, tailscaleIp = tailscaleIp)
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

    fun isTailscaleAddress(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val clean = host.trim().removePrefix("/")
        if (clean.endsWith(".ts.net", ignoreCase = true)) return true
        val parts = clean.split(".")
        if (parts.size != 4) return false
        val b0 = parts[0].toIntOrNull() ?: return false
        val b1 = parts[1].toIntOrNull() ?: return false
        return b0 == 100 && b1 in 64..127
    }

    private fun isPrivateLanBytes(b0: Int, b1: Int): Boolean {
        return b0 == 192 && b1 == 168 ||
            b0 == 10 ||
            (b0 == 172 && b1 in 16..31)
    }
}
