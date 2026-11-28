package com.simorgh.vpn.gateway

import android.os.SystemClock
import com.simorgh.vpn.util.LogBuffer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The sharing gateway (nova LocalAppProxyManager pattern, field-verified).
 *
 * VpnService can only capture traffic BY APP UID — packets the phone forwards
 * from a hotspot client have no UID, so transparent sharing is impossible
 * without root (verified experimentally; VPNHotspot agrees). Instead the
 * phone becomes a GATEWAY: one listener per sharing-interface address speaks
 * both HTTP (CONNECT + absolute-form) and SOCKS5 on the same port (first byte
 * 0x05 = SOCKS5). The proxy's own upstream sockets are the APP's sockets —
 * the app is excluded from the TUN only in the DIRECT direction; dialing
 * 127.0.0.1:socksPort hands the flow to the running Xray socks inbound, so
 * client traffic rides the tunnel on exactly the same terms as app traffic.
 *
 * No-leak policy (nova): when the tunnel is down, clients get a clear 503 /
 * SOCKS 0x02 instead of silently leaving through the censored direct network.
 * An explicit opt-in switch allows direct fallback for those who want it.
 *
 * Auth: password from anywhere; passwordless ONLY for connections accepted
 * on the sharing interface (for TVs/boxes with no credential fields) and
 * only when that mode is enabled.
 */
class GatewayProxy(
    private val socksPort: () -> Int,
    private val tunnelAlive: () -> Boolean,
    private val creds: () -> Pair<String, String>,
    private val noAuthSharing: () -> Boolean,
    private val directWhenDown: () -> Boolean,
    private val sharingAddresses: () -> Set<String>
) {
    private var servers: MutableList<ServerSocket> = mutableListOf()
    private var pool: ExecutorService? = null

    @get:Synchronized
    val running: Boolean
        get() = servers.isNotEmpty()

    @Synchronized
    fun start(addresses: List<String>): Boolean {
        if (addresses.isEmpty()) return false
        stopLocked()
        val p = Executors.newCachedThreadPool { r ->
            Thread(r, "simorgh-gw").apply { isDaemon = true }
        }
        pool = p
        val opened = mutableListOf<ServerSocket>()
        addresses.forEach { addr ->
            runCatching {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(InetAddress.getByName(addr), GatewayEndpoints.PROXY_PORT), 8)
                opened += ss
                p.execute { acceptLoop(ss) }
            }.onFailure {
                LogBuffer.w("Gateway", "cannot bind proxy on $addr:${GatewayEndpoints.PROXY_PORT}: ${it.message}")
            }
        }
        servers = opened
        if (opened.isEmpty()) {
            p.shutdownNow()
            pool = null
            return false
        }
        LogBuffer.i("Gateway", "proxy listening: ${opened.map { it.inetAddress.hostAddress } } port ${GatewayEndpoints.PROXY_PORT}")
        return true
    }

    @Synchronized
    fun stop() = stopLocked()

    private fun stopLocked() {
        servers.forEach { runCatching { it.close() } }
        servers.clear()
        pool?.shutdownNow()
        pool = null
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val sock = try {
                ss.accept()
            } catch (_: Exception) {
                break
            }
            pool?.execute {
                runCatching { handle(sock) }
                runCatching { sock.close() }
            }
        }
    }

    // ------------------------------------------------------------------ dispatch

    private fun handle(sock: Socket) {
        sock.soTimeout = 15_000
        sock.tcpNoDelay = true
        val fromSharing = sock.localAddress?.hostAddress in sharingAddresses()

        val ins = BufferedInputStream(sock.getInputStream(), 16 * 1024)
        val first = ins.read()
        if (first < 0) return
        if (first == 0x05) socks5(sock, ins, fromSharing) else http(sock, ins, fromSharing, first)
    }

    // ------------------------------------------------------------------ HTTP

    private fun http(sock: Socket, ins: BufferedInputStream, fromSharing: Boolean, first: Int) {
        val outs = BufferedOutputStream(sock.getOutputStream(), 16 * 1024)
        var requestLine = byteArrayOf(first.toByte()).toString(Charsets.ISO_8859_1)
        while (true) {
            val line = readLine(ins) ?: return
            if (line.isEmpty()) break
            requestLine += line
            break
        }
        // request line = "METHOD SP TARGET SP HTTP/x.y"
        val sp1 = requestLine.indexOf(' ')
        if (sp1 <= 0) return
        val method = requestLine.substring(0, sp1).uppercase()
        var rest = requestLine.substring(sp1 + 1)
        val sp2 = rest.indexOf(" HTTP/")
        if (sp2 < 0) return
        val target = rest.substring(0, sp2)

        // headers
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(ins) ?: return
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }

        val authorized = authorizeHttp(headers["proxy-authorization"], fromSharing)
        if (!authorized) {
            val msg = "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                "Proxy-Authenticate: Basic realm=\"Simorgh\"\r\n" +
                "Connection: close\r\nContent-Length: 0\r\n\r\n"
            outs.write(msg.toByteArray(Charsets.ISO_8859_1))
            outs.flush()
            return
        }

        if (method == "CONNECT") {
            val host: String
            val port: Int
            val ci = target.lastIndexOf(':')
            if (ci > 0 && target.substring(ci + 1).toIntOrNull() != null) {
                host = target.substring(0, ci)
                port = target.substring(ci + 1).toInt()
            } else {
                host = target
                port = 443
            }
            val up = openUpstream(host, port, outs, "http") ?: return
            outs.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            outs.flush()
            relay(sock, up)
            return
        }

        // plain absolute-form HTTP: http://host[:port]/path → origin-form.
        // (https:// absolute-form appears only from chained proxies — real
        // clients use CONNECT for TLS; refuse it instead of garbling the flow.)
        if (!target.startsWith("http://")) {
            val msg = "HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
            outs.write(msg.toByteArray(Charsets.ISO_8859_1))
            outs.flush()
            return
        }
        val noScheme = target.substringAfter("://")
        val slash = noScheme.indexOf('/')
        val authority = if (slash >= 0) noScheme.substring(0, slash) else noScheme
        val pathAndQuery = if (slash >= 0) noScheme.substring(slash) else "/"
        val hci = authority.lastIndexOf(':')
        val host = if (hci > 0) authority.substring(0, hci) else authority
        val port = if (hci > 0) authority.substring(hci + 1).toIntOrNull() ?: 80 else 80

        val up = openUpstream(host, port, outs, "http") ?: return
        val head = StringBuilder()
            .append(method).append(' ').append(pathAndQuery).append(" HTTP/1.1\r\n")
        headers.forEach { (k, v) ->
            if (k == "proxy-authorization" || k == "proxy-connection") return@forEach
            head.append(k).append(": ").append(v).append("\r\n")
        }
        if (!headers.containsKey("host")) head.append("Host: ").append(authority).append("\r\n")
        head.append("Connection: close\r\n\r\n")
        up.getOutputStream().write(head.toString().toByteArray(Charsets.ISO_8859_1))
        up.getOutputStream().flush()
        relay(sock, up)
    }

    /** 503 with a clear Persian explanation when the tunnel is down (no silent leak). */
    private fun refuseHttp(outs: OutputStream) {
        val body = ("<html dir=\"rtl\"><head><meta charset=\"utf-8\"></head><body>" +
            "<h3>تونل سیمرغ فعال نیست</h3>" +
            "<p>این درگاه فقط وقتی VPN وصل است ترافیک عبور می‌دهد تا چیزی بدون تونل نشت نکند." +
            " روی گوشی سیمرغ را وصل کنید؛ این صفحه خودکار درست می‌شود.</p>" +
            "</body></html>").toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 503 Service Unavailable\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Connection: close\r\nContent-Length: ${body.size}\r\n\r\n"
        outs.write(head.toByteArray(Charsets.ISO_8859_1))
        outs.write(body)
        outs.flush()
    }

    private fun authorizeHttp(proxyAuth: String?, fromSharing: Boolean): Boolean {
        if (noAuthSharing() && fromSharing) return true
        val expected = creds()
        if (expected.first.isBlank()) return false
        if (proxyAuth == null) return false
        val parts = proxyAuth.split(' ', limit = 2)
        if (parts.size != 2 || !parts[0].equals("Basic", true)) return false
        val decoded = runCatching {
            String(Base64.getDecoder().decode(parts[1].trim()), Charsets.UTF_8)
        }.getOrNull() ?: return false
        val ci = decoded.indexOf(':')
        if (ci < 0) return false
        return GatewayAuth.check(expected.first, expected.second, decoded.substring(0, ci), decoded.substring(ci + 1))
    }

    // ------------------------------------------------------------------ SOCKS5

    private fun socks5(sock: Socket, ins: BufferedInputStream, fromSharing: Boolean) {
        val outs = sock.getOutputStream()
        // greeting: VER NMETHODS METHODS…
        val nMethods = ins.read()
        if (nMethods <= 0) return
        val offered = HashSet<Int>()
        repeat(nMethods) {
            val m = ins.read()
            if (m >= 0) offered += m
        }
        val allowNoAuth = noAuthSharing() && fromSharing
        val method: Int = when {
            allowNoAuth && 0x00 in offered -> 0x00
            0x02 in offered -> 0x02
            else -> -1
        }
        if (method == -1) {
            // no acceptable auth method offered (RFC 1928: reply FF, close)
            outs.write(byteArrayOf(0x05, 0xFF.toByte()))
            outs.flush()
            return
        }
        outs.write(byteArrayOf(0x05, method.toByte()))
        outs.flush()

        if (method == 0x02) {
            // user/pass sub-negotiation: VER(01) ULEN UNAME PLEN PASSWD
            if (ins.read() != 0x01) return
            val user = readSized(ins) ?: return
            val pass = readSized(ins) ?: return
            val expected = creds()
            val ok = GatewayAuth.check(expected.first, expected.second, user, pass)
            outs.write(if (ok) byteArrayOf(0x01, 0x00) else byteArrayOf(0x01, 0x01))
            outs.flush()
            if (!ok) return
        }

        // request: VER CMD RSV ATYP ADDR PORT
        if (ins.read() != 0x05) return
        val cmd = ins.read()
        ins.read() // RSV
        val atyp = ins.read()
        val host: String = when (atyp) {
            0x01 -> {
                val b = ByteArray(4)
                if (!readFully(ins, b)) return
                b.joinToString(".") { (it.toInt() and 0xFF).toString() }
            }
            0x03 -> {
                val len = ins.read()
                if (len <= 0) return
                val b = ByteArray(len)
                if (!readFully(ins, b)) return
                String(b, Charsets.US_ASCII)
            }
            0x04 -> {
                val b = ByteArray(16)
                if (!readFully(ins, b)) return
                java.net.InetAddress.getByAddress(b).hostAddress ?: return
            }
            else -> return
        }
        val pb = ByteArray(2)
        if (!readFully(ins, pb)) return
        val port = ((pb[0].toInt() and 0xFF) shl 8) or (pb[1].toInt() and 0xFF)

        if (cmd != 0x01) {
            // only CONNECT — UDP/BIND are refused explicitly
            outs.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            outs.flush()
            return
        }

        if (!tunnelAlive() && !directWhenDown()) {
            // SOCKS5 "connection not allowed by ruleset" — no silent direct leak
            outs.write(byteArrayOf(0x05, 0x02, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            outs.flush()
            return
        }

        val up = dialUpstream(host, port)
        if (up == null) {
            outs.write(byteArrayOf(0x05, 0x03, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // host unreachable
            outs.flush()
            return
        }
        outs.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        outs.flush()
        relay(sock, up)
    }

    // ------------------------------------------------------------------ upstream

    /**
     * Open the upstream for HTTP flows: through the local Xray SOCKS inbound
     * when the tunnel is alive; direct when down AND the user opted in;
     * otherwise a clear 503 and null.
     */
    private fun openUpstream(host: String, port: Int, outs: OutputStream, via: String): Socket? {
        if (!tunnelAlive() && !directWhenDown()) {
            refuseHttp(outs)
            return null
        }
        val s = dialUpstream(host, port)
        if (s == null) {
            val msg = "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
            outs.write(msg.toByteArray(Charsets.ISO_8859_1))
            outs.flush()
        }
        return s
    }

    /** Socket to the target: via local SOCKS (tunnel) or plain direct. */
    private fun dialUpstream(host: String, port: Int): Socket? {
        val t0 = SystemClock.elapsedRealtime()
        val sock = runCatching {
            if (tunnelAlive()) {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort()))
                Socket(proxy).apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(host, port), 12_000)
                }
            } else {
                Socket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(host, port), 12_000)
                }
            }
        }.getOrNull()
        if (sock == null) {
            LogBuffer.d("Gateway", "upstream dial failed $host:$port ($via)")
        } else if (SystemClock.elapsedRealtime() - t0 > 3_000) {
            LogBuffer.d("Gateway", "slow upstream $host:$port ${SystemClock.elapsedRealtime() - t0}ms")
        }
        return sock
    }

    // ------------------------------------------------------------------ helpers

    private fun relay(client: Socket, upstream: Socket) {
        // a tunnel connection may be silent for minutes (chat, push): no read
        // timeout once the flow is established
        runCatching { client.soTimeout = 0 }
        runCatching { upstream.soTimeout = 0 }
        val a = Thread { pump(client.getInputStream(), upstream.getOutputStream(), upstream, client) }
        val b = Thread { pump(upstream.getInputStream(), client.getOutputStream(), client, upstream) }
        a.isDaemon = true
        b.isDaemon = true
        a.start()
        b.start()
        try {
            a.join(120_000)
            b.join(120_000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            runCatching { upstream.close() }
            runCatching { client.close() }
        }
    }

    private fun pump(src: InputStream, dst: OutputStream, vararg closeOnEnd: Socket) {
        runCatching {
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = src.read(buf)
                if (n < 0) break
                if (n > 0) {
                    dst.write(buf, 0, n)
                    dst.flush()
                }
            }
        }
        closeOnEnd.forEach { runCatching { it.close() } }
    }

    private fun readLine(ins: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = ins.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 16 * 1024) return null
        }
    }

    private fun readSized(ins: InputStream): String? {
        val len = ins.read()
        if (len <= 0) return null
        val b = ByteArray(len)
        if (!readFully(ins, b)) return null
        return String(b, Charsets.UTF_8)
    }

    private fun readFully(ins: InputStream, b: ByteArray): Boolean {
        var off = 0
        while (off < b.size) {
            val n = ins.read(b, off, b.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }
}
