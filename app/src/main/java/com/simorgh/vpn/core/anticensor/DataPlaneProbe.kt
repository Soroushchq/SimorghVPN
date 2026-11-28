package com.simorgh.vpn.core.anticensor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * Real data-plane probe (nova/msn-guard pattern): "is the core running" is
 * NOT "is the tunnel alive". After every engine start we push one real HTTP
 * request through the local SOCKS inbound — it must travel the whole chain
 * (socks → Xray transport → server → internet) and come back 2xx.
 *
 * The app package is excluded from the TUN in every tunnel mode, so this
 * socket never loops back into itself; loopback to 127.0.0.1 is direct by
 * definition and only the SOCKS hop is proxied. DNS for the probe host is
 * resolved by the remote proxy (Java SOCKS = remote resolve), so a poisoned
 * local resolver cannot fake a success either.
 */
object DataPlaneProbe {

    private const val PROBE_URL = "http://cp.cloudflare.com/generate_204"
    const val DEFAULT_TIMEOUT_MS = 8_000L

    /**
     * @return true only when a real end-to-end request succeeded.
     *         false = silent death (handshake blackhole, RST, DNS kill...).
     */
    suspend fun ok(
        socksPort: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val conn = URL(PROBE_URL).openConnection(proxy) as HttpURLConnection
            try {
                conn.connectTimeout = timeoutMs.toInt()
                conn.readTimeout = timeoutMs.toInt()
                conn.useCaches = false
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "SimorghProbe/1.0")
                val code = conn.responseCode
                code in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }
}
