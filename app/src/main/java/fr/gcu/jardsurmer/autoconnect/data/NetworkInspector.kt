package fr.gcu.jardsurmer.autoconnect.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInspector {
    fun findWifiNetwork(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null

        // 1. Check bound process network first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val bound = cm.boundNetworkForProcess
            if (bound != null) {
                val caps = cm.getNetworkCapabilities(bound)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    return bound
                }
            }
        }

        // 2. Check active network
        val active = cm.activeNetwork
        if (active != null) {
            val caps = cm.getNetworkCapabilities(active)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                return active
            }
        }

        // 3. Search all networks for Wi-Fi transport
        @Suppress("DEPRECATION")
        val networks = cm.allNetworks
        for (network in networks) {
            val capabilities = cm.getNetworkCapabilities(network) ?: continue
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return network
            }
        }
        return null
    }

    fun isGcuCandidate(context: Context, network: Network?): Boolean {
        if (network == null) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val linkProps = cm.getLinkProperties(network)
        if (linkProps != null) {
            for (route in linkProps.routes) {
                val gateway = route.gateway
                if (gateway != null && gateway.hostAddress == "192.168.182.1") {
                    return true
                }
            }
            for (addr in linkProps.linkAddresses) {
                val ip = addr.address
                if (ip is Inet4Address && is192168182Subnet(ip.hostAddress)) {
                    return true
                }
            }
        }

        // Check local network interfaces fallback
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && is192168182Subnet(addr.hostAddress)) {
                        return true
                    }
                }
            }
        } catch (_: Throwable) {}

        // Check Wifi SSID as secondary fallback
        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid?.lowercase() ?: ""
            if (ssid.contains("gcu") || ssid.contains("jard")) {
                return true
            }
        }

        return false
    }

    private fun is192168182Subnet(ip: String?): Boolean {
        if (ip == null) return false
        return ip.startsWith("192.168.182.")
    }
}
