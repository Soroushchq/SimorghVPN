package com.simorgh.vpn.gateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import com.simorgh.vpn.util.LogBuffer
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Sharing-interface detection (nova GatewayEndpoints pattern, field-verified).
 *
 * The one true sign of the sharing side of a connection is the ABSENCE of
 * NET_CAPABILITY_INTERNET: the sharing side leads nowhere, it only accepts
 * clients. Uplinks (Wi-Fi client, cellular, VPN) always have it.
 *
 * Secondary filter by interface name: 464XLAT (clat*), the VPN's own tun*,
 * cellular rmnet* and system dummies must never count as sharing sides even
 * if a ROM marks them oddly.
 *
 * Subnets are NOT hardcoded — on real devices the classic 192.168.43.1 was
 * never seen; addresses come from the interface itself.
 */
object GatewayEndpoints {

    const val PROXY_PORT = 1370
    const val PORTAL_PORT = 1371

    data class SharingIface(val name: String, val addresses: List<String>)

    /** Service-like interface prefixes that are never the sharing side. */
    private val EXCLUDED_PREFIXES = listOf("v4-", "clat", "tun", "rmnet", "dummy", "ppp", "sit", "ip6tnl")

    fun detect(context: Context): List<SharingIface> {
        val cm = runCatching {
            context.applicationContext.getSystemService(ConnectivityManager::class.java)
        }.getOrNull() ?: run {
            LogBuffer.w("Gateway", "no ConnectivityManager — cannot detect sharing interfaces")
            return emptyList()
        }

        val out = LinkedHashMap<String, SharingIface>()
        for (network in cm.allNetworks) {
            val caps = runCatching { cm.getNetworkCapabilities(network) }.getOrNull()
            val lp: LinkProperties = runCatching { cm.getLinkProperties(network) }.getOrNull()
                ?: continue

            val iface = lp.interfaceName ?: continue
            val isSharing = caps != null &&
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            val excluded = EXCLUDED_PREFIXES.any { iface.startsWith(it) }

            if (isSharing && !excluded && iface !in out) {
                val addrs = ipv4Addresses(iface)
                if (addrs.isNotEmpty()) {
                    out[iface] = SharingIface(iface, addrs)
                } else {
                    LogBuffer.d("Gateway", "$iface looks like sharing but has no IPv4 address")
                }
            } else if (BuildLike.DEBUG) {
                LogBuffer.d(
                    "Gateway",
                    "net iface=$iface sharing=$isSharing excluded=$excluded caps=${caps?.toString()?.take(80)}"
                )
            }
        }
        if (out.isEmpty()) {
            LogBuffer.d("Gateway", "no sharing interfaces detected (hotspot/USB tethering off?)")
        }
        return out.values.toList()
    }

    /** Site IPv4 addresses of one interface (loopback/link-local skipped). */
    fun ipv4Addresses(ifaceName: String): List<String> = runCatching {
        val ni = NetworkInterface.getByName(ifaceName) ?: return@runCatching emptyList()
        ni.inetAddresses.asSequence()
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .map { it.hostAddress ?: "" }
            .filter { it.isNotBlank() }
            .toList()
    }.getOrDefault(emptyList())

    /** All sharing-side IPv4 addresses flattened. */
    fun sharingAddresses(ifaces: List<SharingIface>): Set<String> =
        ifaces.flatMap { it.addresses }.toSet()

    private object BuildLike {
        // debug-detail flag; keep it on so field issues are diagnosable from logs
        const val DEBUG = true
    }
}
