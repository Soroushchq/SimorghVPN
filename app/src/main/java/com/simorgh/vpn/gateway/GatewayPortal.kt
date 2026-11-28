package com.simorgh.vpn.gateway

import com.simorgh.vpn.util.LogBuffer
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The sharing portal (nova GatewayPortal pattern): a tiny HTTP page on a
 * separate port served to hotspot clients — connection parameters, step-by-
 * step setup instructions and a PAC file, so the client configures itself
 * with zero typing.
 *
 * Credentials are shown ONLY to connections accepted on a sharing-interface
 * address: such a connection physically cannot arrive from any other network.
 * A client from the common (non-sharing) network gets the same page minus the
 * credentials.
 */
class GatewayPortal(
    private val creds: () -> Pair<String, String>,
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
            Thread(r, "simorgh-portal").apply { isDaemon = true }
        }
        pool = p
        val opened = mutableListOf<ServerSocket>()
        addresses.forEach { addr ->
            runCatching {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(InetAddress.getByName(addr), GatewayEndpoints.PORTAL_PORT), 4)
                opened += ss
                p.execute { acceptLoop(ss) }
            }.onFailure {
                LogBuffer.w("Gateway", "cannot bind portal on $addr:${GatewayEndpoints.PORTAL_PORT}: ${it.message}")
            }
        }
        servers = opened
        if (opened.isEmpty()) {
            p.shutdownNow()
            pool = null
            return false
        }
        LogBuffer.i("Gateway", "portal listening: ${opened.map { it.inetAddress.hostAddress }} port ${GatewayEndpoints.PORTAL_PORT}")
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
                runCatching { serve(sock) }
                runCatching { sock.close() }
            }
        }
    }

    private fun serve(sock: Socket) {
        sock.soTimeout = 10_000
        val ins = sock.getInputStream()
        val outs = BufferedOutputStream(sock.getOutputStream(), 8 * 1024)
        // request line + headers (drained, ignored beyond the path)
        var path = "/"
        var first = true
        while (true) {
            val line = readLine(ins) ?: break
            if (line.isEmpty()) break
            if (first) {
                val parts = line.split(' ')
                if (parts.size >= 2) path = parts[1].substringBefore('?')
                first = false
            }
        }
        val localAddr = sock.localAddress?.hostAddress ?: ""
        val fromSharing = localAddr in sharingAddresses()

        when {
            path == "/proxy.pac" -> respond(
                outs, "application/x-ns-proxy-autoconfig", pac(localAddr).toByteArray(Charsets.UTF_8)
            )
            else -> respond(
                outs, "text/html; charset=utf-8", page(localAddr, fromSharing).toByteArray(Charsets.UTF_8)
            )
        }
    }

    private fun respond(outs: java.io.OutputStream, contentType: String, body: ByteArray) {
        val head = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: $contentType\r\n" +
            "Connection: close\r\n" +
            "Content-Length: ${body.size}\r\n\r\n"
        outs.write(head.toByteArray(Charsets.ISO_8859_1))
        outs.write(body)
        outs.flush()
    }

    /**
     * PAC (nova rules): never call isInNet on a NAME — that does a synchronous
     * DNS lookup in the client for every URL. Literal IPs only, and the proxy
     * for everything else.
     */
    private fun pac(localAddr: String): String {
        val host = if (localAddr in sharingAddresses()) localAddr else "<gateway-ip>"
        return "function FindProxyForURL(url, host) {\n" +
            "  if (isPlainHostName(host) || shExpMatch(host, \"*.local\")) return \"DIRECT\";\n" +
            "  return \"PROXY $host:${GatewayEndpoints.PROXY_PORT}\";\n" +
            "}\n"
    }

    /** Persian RTL setup page. Credentials only for sharing-side clients. */
    private fun page(localAddr: String, fromSharing: Boolean): String {
        val (user, pass) = creds()
        val host = if (fromSharing) localAddr else "<آدرس-گوشی-سیمرغ>"
        val credsBlock = if (fromSharing) {
            """<tr><td>نام کاربری</td><td dir="ltr"><b>$user</b></td></tr>
               <tr><td>رمز عبور</td><td dir="ltr"><b>$pass</b></td></tr>"""
        } else {
            """<tr><td colspan="2">نام کاربری و رمز فقط از شبکه‌ی همین هات‌اسپات نمایش داده می‌شود — از خود گوشی یا یک کلاینت متصل به این شبکه باز کنید.</td></tr>"""
        }
        return """<!DOCTYPE html>
<html dir="rtl" lang="fa"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>سیمرغ — درگاه اشتراک</title>
<style>
body{font-family:sans-serif;background:#faf7f2;color:#31403a;margin:0;padding:20px}
.card{max-width:520px;margin:12px auto;background:#fff;border-radius:14px;padding:18px 20px;box-shadow:0 2px 10px rgba(0,0,0,.08)}
h1{font-size:20px;color:#b3541e}h2{font-size:16px;margin:18px 0 6px}
table{width:100%;border-collapse:collapse}td{padding:6px 4px;border-bottom:1px solid #eee;font-size:14px}
code{background:#f2efe9;border-radius:6px;padding:2px 8px;direction:ltr;display:inline-block}
ol{padding-inline-start:20px;font-size:14px;line-height:2}
.note{font-size:12px;color:#777;margin-top:14px;line-height:1.8}
</style></head><body>
<div class="card">
<h1>سیمرغ — درگاه اشتراک اینترنت</h1>
<p style="font-size:14px">این گوشی به‌عنوان درگاه VPN کار می‌کند. یک‌بار پروکسی را در دستگاه کلاینت تنظیم کن؛ از آن پس همه‌چیز از تونل سیمرغ عبور می‌کند.</p>
<h2>مشخصات اتصال</h2>
<table>
<tr><td>پروکسی (HTTP و SOCKS5)</td><td dir="ltr"><code>$host:${GatewayEndpoints.PROXY_PORT}</code></td></tr>
<tr><td>پروکسی خودکار (PAC)</td><td dir="ltr"><code>http://$host:${GatewayEndpoints.PORTAL_PORT}/proxy.pac</code></td></tr>
$credsBlock
</table>
<h2>مراحل تنظیم کلاینت</h2>
<ol>
<li>در وای‌فای همان شبکه، گزینه‌ی «پروکسی» را روی «دستی» بگذار.</li>
<li>میزبان: <code>$host</code> — پورت: <code>${GatewayEndpoints.PROXY_PORT}</code></li>
<li>نام کاربری و رمز بالا را وارد کن (اندروید/ویندوز/iOS همه پشتیبانی می‌کنند).</li>
<li>اگر دستگاه «PAC» دارد، به‌جای تنظیم دستی آدرس <code>http://$host:${GatewayEndpoints.PORTAL_PORT}/proxy.pac</code> را بده.</li>
</ol>
<div class="note">
تلفن‌ها و تلویزیون‌هایی که فیلد نام کاربری ندارند: در تنظیمات سیمرغ «حالت بدون رمز برای کلاینت‌های هات‌اسپات» را روشن کن.<br>
اگر پروکسی socks قابل انتخاب است، <code>socks5h</code> را به <code>socks5</code> ترجیح بده تا DNS هم از تونل برود.<br>
وقتی VPN قطع است، درگاه به‌جای عبور ناامن، پیام «سرویس در دسترس نیست» می‌دهد؛ با وصل‌شدن VPN خودکار درست می‌شود.<br>
فقط ترافیک TCP عبور می‌کند (وب و اپ‌ها)؛ بازی‌های آنلاین و تماس‌های UDP از پروکسی بیرون می‌مانند.
</div>
</div></body></html>"""
    }

    private fun readLine(ins: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = ins.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 8 * 1024) return null
        }
    }
}
