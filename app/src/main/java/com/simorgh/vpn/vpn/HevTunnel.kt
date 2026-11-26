package com.simorgh.vpn.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import com.simorgh.vpn.data.SimorghSettings
import com.simorgh.vpn.util.LogBuffer
import hev.htproxy.TProxyService as HevNative
import java.io.File

/**
 * tun2socks bridge — official hev-socks5-tunnel aar.
 * Config format mirrors Derafsh/v2rayNG exactly.
 */
object HevTunnel {

    @Volatile
    var running = false
        private set

    fun start(context: Context, tun: ParcelFileDescriptor, s: SimorghSettings) {
        val yaml = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: ${s.mtu}")
            appendLine("  ipv4: 10.10.14.2")
            if (s.ipv6) appendLine("  ipv6: 'fc00::10:10:14:2'")
            appendLine("socks5:")
            appendLine("  port: ${s.socksPort}")
            appendLine("  address: 127.0.0.1")
            appendLine("  udp: 'udp'")
            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  udp-read-write-timeout: 60000")
            appendLine("  log-level: warn")
        }
        val f = File(context.applicationContext.filesDir, "hev-socks5-tunnel.yaml")
        f.writeText(yaml)
        val ok = runCatching { HevNative.TProxyStartService(f.absolutePath, tun.fd) }
            .getOrElse {
                LogBuffer.e("Hev", "start failed", it)
                false
            }
        running = ok
        LogBuffer.i("Hev", "hev-socks5-tunnel start -> $ok")
    }

    fun stop() {
        runCatching { HevNative.TProxyStopService() }
            .onFailure { LogBuffer.w("Hev", "stop: ${it.message}") }
        running = false
    }
}
