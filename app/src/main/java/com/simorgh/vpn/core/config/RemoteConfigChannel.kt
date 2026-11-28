package com.simorgh.vpn.core.config

import android.content.Context
import android.util.Base64
import com.simorgh.vpn.data.SimorghSettings
import com.simorgh.vpn.util.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec

/**
 * Fetch → verify → apply pipeline for the signed remote-config channel.
 *
 * The channel URL (default: a rolling `remote-config` release on GitHub) serves
 * `remote-config.json` plus `remote-config.json.sig` (base64 Ed25519 over the
 * exact payload bytes). The public key is pinned below; a config without a
 * valid signature is refused — a MITM or a compromised mirror can never
 * reshape the anti-censorship parameters.
 *
 * Fetching rides the tunnel when it is up (our app is tun-excluded, so a
 * direct socket would bypass the proxy; GitHub is frequently throttled in
 * Iran), and falls back to direct when it is not.
 */
object RemoteConfigChannel {

    const val DEFAULT_URL =
        "https://github.com/Soroushchq/SimorghVPN/releases/download/remote-config/remote-config.json"

    /** Ed25519 public key (X.509 SubjectPublicKeyInfo, base64) — pairs with
     *  the repo secret REMOTE_CONFIG_SIGNING_KEY. */
    const val PUBLIC_KEY_B64 =
        "MCowBQYDK2VwAyEAYtQfH9SN8l/hrlvgjm8OghXCb58UTtKBaAjYzOyYBXA="

    private const val TIMEOUT_MS = 10_000
    private const val MAX_PAYLOAD = 256 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun file(context: Context) =
        File(context.applicationContext.filesDir, "remote-config-applied.json")

    data class ApplyResult(val applied: Boolean, val notes: String, val reason: String = "")

    /** Fetch, verify and apply now. Never throws — failures keep the current state. */
    suspend fun checkAndApply(context: Context, settings: SimorghSettings): ApplyResult =
        withContext(Dispatchers.IO) {
            val url = settings.remoteConfigUrl.trim().ifBlank { DEFAULT_URL }
            try {
                val payload = fetch(url, settings.socksPort) ?: return@withContext ApplyResult(false, "", "fetch failed")
                if (payload.size > MAX_PAYLOAD) return@withContext ApplyResult(false, "", "payload too large")
                val sig = fetch("$url.sig", settings.socksPort)
                    ?: return@withContext ApplyResult(false, "", "signature fetch failed")
                val sigText = sig.toString(Charsets.UTF_8).trim()
                if (!verify(payload, sigText)) {
                    LogBuffer.w("RemoteConfig", "signature INVALID — ignoring remote config")
                    return@withContext ApplyResult(false, "", "bad signature")
                }
                val cfg = json.decodeFromString<RemoteConfig>(payload.toString(Charsets.UTF_8))
                RemoteOverrides.apply(cfg)
                persist(context, payload)
                LogBuffer.i(
                    "RemoteConfig",
                    "applied: ${cfg.fragmentProfiles.size} fragment profiles, " +
                        "${cfg.fingerprints.size} fingerprints, ${cfg.warpEndpoints.size} warp endpoints" +
                        if (cfg.notes.isNotBlank()) " — ${cfg.notes}" else ""
                )
                ApplyResult(true, cfg.notes)
            } catch (t: Throwable) {
                LogBuffer.w("RemoteConfig", "check failed: ${t.message}")
                ApplyResult(false, "", t.message ?: "error")
            }
        }

    /** Reload the last verified config from disk (offline restarts keep it). */
    fun loadPersisted(context: Context): Boolean = runCatching {
        val f = file(context)
        if (!f.exists()) return false
        val cfg = json.decodeFromString<RemoteConfig>(f.readText())
        RemoteOverrides.apply(cfg)
        true
    }.getOrDefault(false)

    private fun persist(context: Context, payload: ByteArray) {
        runCatching {
            val f = file(context)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(payload.toString(Charsets.UTF_8))
            if (!tmp.renameTo(f)) {
                f.delete()
                check(tmp.renameTo(f)) { "remote-config rename failed" }
            }
        }.onFailure { LogBuffer.w("RemoteConfig", "persist failed: ${it.message}") }
    }

    // ------------------------------------------------------------------ crypto

    /** Ed25519 verification (net.i2p.crypto:eddsa — no provider registration needed). */
    fun verify(payload: ByteArray, sigBase64: String): Boolean = runCatching {
        val pub = EdDSAPublicKey(
            X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_B64, Base64.NO_WRAP))
        )
        val engine = EdDSAEngine(MessageDigest.getInstance("SHA-512"))
        engine.initVerify(pub)
        engine.update(payload)
        engine.verify(Base64.decode(sigBase64, Base64.NO_WRAP))
    }.getOrDefault(false)

    // ------------------------------------------------------------------ http

    private fun fetch(url: String, socksPort: Int): ByteArray? = runCatching {
        val conn = if (socksAlive(socksPort)) {
            URL(url).openConnection(
                Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            ) as HttpURLConnection
        } else {
            URL(url).openConnection() as HttpURLConnection
        }
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.useCaches = false
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "SimorghRemoteConfig/1.0")
            val code = conn.responseCode
            if (code !in 200..299) return@runCatching null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) null else bytes
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun socksAlive(port: Int): Boolean = runCatching {
        java.net.Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", port), 500)
            true
        }
    }.getOrDefault(false)
}
