package fr.gcu.jardsurmer.autoconnect.data

import android.content.Context
import android.net.Network
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class DnsResolver(
    private val context: Context,
    private val network: Network?
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.equals("jard-sur-mer.gcuf.fr", ignoreCase = true)) {
            // First attempt normal resolution on network
            if (network != null) {
                try {
                    val resolved = network.getAllByName(hostname).toList()
                    if (resolved.isNotEmpty()) return resolved
                } catch (_: UnknownHostException) {}
            }
            try {
                val resolved = InetAddress.getAllByName(hostname).toList()
                if (resolved.isNotEmpty()) return resolved
            } catch (_: UnknownHostException) {}

            // Direct fallback to portal IP 192.168.182.1
            try {
                return listOf(InetAddress.getByAddress(hostname, byteArrayOf(192.toByte(), 168.toByte(), 182.toByte(), 1.toByte())))
            } catch (_: Throwable) {}
        }

        if (network != null) {
            try {
                return network.getAllByName(hostname).toList()
            } catch (_: Throwable) {}
        }
        return InetAddress.getAllByName(hostname).toList()
    }
}
