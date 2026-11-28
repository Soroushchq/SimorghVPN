package com.simorgh.vpn.core.warp

import com.simorgh.vpn.core.config.RemoteOverrides

/**
 * Cloudflare WARP dial coordinates (wgcf / warp-plus knowledge base).
 *
 * Endpoints are dialed by IP on purpose: a hostname endpoint would need a
 * DNS lookup through Xray's DNS module, whose remote server routes through
 * the proxy — i.e. through the WireGuard tunnel that does not exist yet
 * (chicken-and-egg). The anycast IPs below all serve WireGuard.
 *
 * v0.4: the lists consult the signed remote-config channel, so endpoints can
 * be reshaped the moment Cloudflare coordinates change — no release needed.
 */
object WarpEndpoints {

    /** The one and only Cloudflare WARP peer public key. */
    const val PEER_PUBLIC_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

    /** Built-in WARP anycast IPs (162.159.192.x/193.x + 188.114.96/97.x engage VIPs). */
    val DEFAULT_ANYCAST_IPS = listOf(
        "162.159.192.1",
        "162.159.192.7",
        "162.159.193.10",
        "162.159.193.7",
        "162.159.192.4",
        "188.114.96.1",
        "188.114.97.1",
        "162.159.195.49"
    )

    /**
     * Ports Cloudflare WARP answers WireGuard on (official alternates,
     * verified by the warp-plus community scanners). The first entry is
     * the well-known default.
     */
    val DEFAULT_PORTS = listOf(2408, 894, 943, 500, 1701, 1843, 2371, 2506, 3138, 3581, 4233)

    const val DEFAULT_PORT = 2408
    const val WG_MTU = 1280

    /** Live anycast list: remote override → built-ins. */
    fun anycastIps(): List<String> =
        RemoteOverrides.warpIps?.takeIf { it.isNotEmpty() } ?: DEFAULT_ANYCAST_IPS

    /** Live port list: remote override → built-ins. */
    fun ports(): List<Int> =
        RemoteOverrides.warpPorts?.takeIf { it.isNotEmpty() } ?: DEFAULT_PORTS
}
