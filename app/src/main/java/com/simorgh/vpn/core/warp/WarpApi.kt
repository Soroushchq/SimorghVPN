package com.simorgh.vpn.core.warp

import com.simorgh.vpn.core.crypto.Curve25519
import com.simorgh.vpn.util.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.security.SecureRandom

/**
 * Anonymous WARP device registration against the Cloudflare client API
 * (wgcf / warp-plus flow): mint an X25519 keypair locally, POST only the
 * public key, get back the assigned tunnel addresses + client id.
 *
 * Reachability ladder:
 *  1. direct — api.cloudflareclient.com is normally reachable even under censorship
 *  2. through the local SOCKS port — when an Xray profile is already carrying
 *     the tunnel (the app itself is excluded from the VPN TUN, so its direct
 *     traffic does NOT ride the tunnel — we have to dial the proxy explicitly)
 */
object WarpApi {

    private const val REG_URL = "https://api.cloudflareclient.com/v0a2158/reg"
    private const val CF_CLIENT_VERSION = "a-7.21-2158"
    private const val TIMEOUT_MS = 15000

    /**
     * Registers a brand-new WARP device.
     *
     * @param socksPort local Xray SOCKS port used as fallback path (0 = skip)
     */
    suspend fun registerNew(socksPort: Int): Result<WarpIdentity> = withContext(Dispatchers.IO) {
        runCatching {
            val random = SecureRandom()
            val priv = Curve25519.generatePrivateKey(random)
            val pub = Curve25519.publicKey(priv)

            val b64Priv = java.util.Base64.getEncoder().encodeToString(priv)
            val b64Pub = java.util.Base64.getEncoder().encodeToString(pub)
            val serial = ByteArray(8).also { random.nextBytes(it) }
                .joinToString("") { "%02x".format(it) }

            val body = buildJsonObject {
                put("fcm_token", "")
                put("install_id", "")
                put("key", b64Pub)
                put("locale", "en_US")
                put("model", "Android")
                put("serial_number", serial)
                put("tos", "2024-01-01T00:00:00.000Z")
                put("type", "Android")
            }.toString()

            val json = post(body, proxy = null)
                .recoverCatching {
                    LogBuffer.w("Warp", "direct registration failed (${it.message}) — trying via tunnel SOCKS :$socksPort")
                    if (socksPort <= 0) throw it
                    post(body, proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
                        .getOrThrow()
                }.getOrThrow()

            parseResponse(json, b64Priv, b64Pub)
        }
    }

    // ------------------------------------------------------------------ http

    private fun post(body: String, proxy: Proxy?): Result<String> = runCatching {
        val conn = URI(REG_URL).toURL().openConnection(proxy) as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("CF-Client-Version", CF_CLIENT_VERSION)
        conn.setRequestProperty("User-Agent", "okhttp/3.12.1")

        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        if (code !in 200..299) error("HTTP $code: ${text.take(200)}")
        text
    }

    private fun parseResponse(json: String, b64Priv: String, b64Pub: String): WarpIdentity {
        val root = Json.parseToJsonElement(json).jsonObject
        val id = root["id"]?.jsonPrimitive?.content
            ?: error("registration response missing id")
        val clientId = root["account"]?.jsonObject?.get("client_id")?.jsonPrimitive?.content
            ?: root["config"]?.jsonObject?.get("client_id")?.jsonPrimitive?.content
            ?: ""
        val iface = root["config"]?.jsonObject?.get("interface")?.jsonObject
            ?: error("registration response missing config.interface")
        val addrs = iface["addresses"]?.jsonObject
            ?: error("registration response missing addresses")
        val v4 = addrs["v4"]?.jsonPrimitive?.content
            ?: error("registration response missing v4 address")
        val v6 = addrs["v6"]?.jsonPrimitive?.content ?: ""

        if (clientId.isBlank()) error("registration response missing client_id")
        return WarpIdentity(
            id = id,
            privateKey = b64Priv,
            publicKey = b64Pub,
            v4 = v4,
            v6 = v6,
            clientIdHex = clientId,
            createdAt = System.currentTimeMillis()
        )
    }
}
