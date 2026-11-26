package com.simorgh.vpn.data

import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import java.util.UUID

/**
 * Parser for share links (vless/vmess/trojan/ss) and subscription bodies.
 * Formats follow the de-facto v2rayNG / Nekoray conventions.
 */
object ShareLinkParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun newId(): String = UUID.randomUUID().toString()

    fun parseLine(line: String): Profile? {
        val s = line.trim()
        if (s.length < 8) return null
        return when {
            s.startsWith("vless://", true) -> parseVless(s)
            s.startsWith("vmess://", true) -> parseVmess(s)
            s.startsWith("trojan://", true) -> parseTrojan(s)
            s.startsWith("ss://", true) -> parseSs(s)
            else -> null
        }
    }

    fun parseAll(text: String): List<Profile> = text.lines().mapNotNull { parseLine(it) }

    /** Subscription body: either plain links or a base64 blob. */
    fun parseSubscriptionBody(body: String): List<Profile> {
        val direct = parseAll(body)
        if (direct.isNotEmpty()) return direct
        val cleaned = body.trim()
        if (cleaned.isEmpty()) return emptyList()
        val decoded = decodeBase64Lenient(cleaned) ?: return emptyList()
        return parseAll(decoded)
    }

    // ------------------------------------------------------------------ vless

    private fun parseVless(s: String): Profile? {
        val uri = runCatching { Uri.parse(s) }.getOrNull() ?: return null
        val uuid = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        fun q(k: String): String = runCatching { uri.getQueryParameter(k) }.getOrNull() ?: ""
        val security = q("security").ifBlank { "none" }.lowercase()
        return Profile(
            id = newId(),
            name = uri.fragment?.takeIf { it.isNotBlank() } ?: "$host:$port",
            protocol = "vless",
            host = host,
            port = port,
            secret = uuid,
            encryption = q("encryption").ifBlank { "none" },
            flow = q("flow"),
            network = q("type").ifBlank { "tcp" }.lowercase(),
            security = when (security) {
                "reality" -> "reality"
                "tls", "xtls" -> "tls"
                else -> "none"
            },
            sni = q("sni"),
            hostHeader = q("host"),
            path = q("path"),
            serviceName = q("serviceName"),
            fingerprint = q("fp"),
            publicKey = q("pbk"),
            shortId = q("sid"),
            alpn = q("alpn"),
            allowInsecure = q("allowInsecure") == "1" || q("insecure") == "1"
        )
    }

    // ------------------------------------------------------------------ vmess

    private fun parseVmess(s: String): Profile? {
        val b64 = s.substringAfter("vmess://")
        val text = decodeBase64Lenient(b64) ?: return null
        val root = runCatching { json.parseToJsonElement(text.trim()).jsonObject }.getOrNull() ?: return null
        fun str(k: String): String =
            runCatching { root[k]!!.jsonPrimitive.content }.getOrNull() ?: ""
        val host = str("add")
        val port = str("port").toIntOrNull() ?: return null
        val net = str("net").ifBlank { "tcp" }.lowercase()
        val name = str("ps").ifBlank { "$host:$port" }
        return Profile(
            id = newId(),
            name = name,
            protocol = "vmess",
            host = host,
            port = port,
            secret = str("id"),
            encryption = str("scy").ifBlank { "auto" },
            network = net,
            security = if (str("tls") == "tls") "tls" else "none",
            sni = str("sni"),
            hostHeader = str("host"),
            path = str("path"),
            serviceName = str("path"),
            fingerprint = str("fp"),
            alpn = str("alpn")
        )
    }

    // ------------------------------------------------------------------ trojan

    private fun parseTrojan(s: String): Profile? {
        val uri = runCatching { Uri.parse(s) }.getOrNull() ?: return null
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        fun q(k: String): String = runCatching { uri.getQueryParameter(k) }.getOrNull() ?: ""
        val security = q("security").ifBlank { "tls" }.lowercase()
        return Profile(
            id = newId(),
            name = uri.fragment?.takeIf { it.isNotBlank() } ?: "$host:$port",
            protocol = "trojan",
            host = host,
            port = port,
            secret = password,
            network = q("type").ifBlank { "tcp" }.lowercase(),
            security = if (security == "none") "none" else "tls",
            sni = q("sni"),
            hostHeader = q("host"),
            path = q("path"),
            serviceName = q("serviceName"),
            fingerprint = q("fp"),
            alpn = q("alpn"),
            allowInsecure = q("allowInsecure") == "1" || q("insecure") == "1"
        )
    }

    // ------------------------------------------------------------------ shadowsocks

    private fun parseSs(s: String): Profile? {
        val body = s.substringAfter("ss://")
        val name = Uri.decode(body.substringAfterLast("#", "")).takeIf { it.isNotBlank() } ?: ""
        val main = body.substringBefore("#")
        if (main.isBlank()) return null
        if (main.contains("@")) {
            // SIP002: ss://base64(method:pass)@host:port or ss://method:pass@host:port
            val userinfo = main.substringBeforeLast("@")
            val hostPort = main.substringAfterLast("@")
            val host = hostPort.substringBeforeLast(":")
            val port = hostPort.substringAfterLast(":").toIntOrNull() ?: return null
            if (host.isBlank()) return null
            val methodPass = if (userinfo.contains(":")) Uri.decode(userinfo)
            else decodeBase64Lenient(userinfo) ?: return null
            val method = methodPass.substringBefore(":")
            val pass = methodPass.substringAfter(":")
            if (method.isBlank() || pass.isBlank()) return null
            return ssProfile(name, host, port, method, pass)
        }
        // legacy: ss://base64(method:pass@host:port)
        val decoded = decodeBase64Lenient(main) ?: return null
        val userinfo = decoded.substringBeforeLast("@")
        val hostPort = decoded.substringAfterLast("@")
        val host = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: return null
        val method = userinfo.substringBefore(":")
        val pass = userinfo.substringAfter(":")
        if (host.isBlank() || method.isBlank()) return null
        return ssProfile(name, host, port, method, pass)
    }

    private fun ssProfile(name: String, host: String, port: Int, method: String, pass: String) = Profile(
        id = newId(),
        name = name.ifBlank { "$host:$port" },
        protocol = "shadowsocks",
        host = host,
        port = port,
        secret = pass,
        encryption = method
    )

    // ------------------------------------------------------------------ export

    /** Rebuilds a shareable link for the profile. */
    fun toLink(p: Profile): String = when (p.protocol) {
        "vless" -> {
            val q = buildString {
                append("encryption=${p.encryption.ifBlank { "none" }}")
                append("&security=${p.security}")
                append("&type=${p.network}")
                if (p.security != "none") {
                    if (p.sni.isNotBlank()) append("&sni=${Uri.encode(p.sni)}")
                    if (p.fingerprint.isNotBlank()) append("&fp=${p.fingerprint}")
                    if (p.security == "reality") {
                        if (p.publicKey.isNotBlank()) append("&pbk=${Uri.encode(p.publicKey)}")
                        if (p.shortId.isNotBlank()) append("&sid=${Uri.encode(p.shortId)}")
                    }
                }
                if (p.flow.isNotBlank()) append("&flow=${Uri.encode(p.flow)}")
                if (p.hostHeader.isNotBlank()) append("&host=${Uri.encode(p.hostHeader)}")
                if (p.path.isNotBlank()) append("&path=${Uri.encode(p.path)}")
                if (p.serviceName.isNotBlank()) append("&serviceName=${Uri.encode(p.serviceName)}")
                if (p.alpn.isNotBlank()) append("&alpn=${Uri.encode(p.alpn)}")
            }
            "vless://${p.secret}@${p.host}:${p.port}?$q#${Uri.encode(p.name)}"
        }
        "trojan" -> {
            val q = buildString {
                append("security=${p.security}&type=${p.network}")
                if (p.sni.isNotBlank()) append("&sni=${Uri.encode(p.sni)}")
                if (p.hostHeader.isNotBlank()) append("&host=${Uri.encode(p.hostHeader)}")
                if (p.path.isNotBlank()) append("&path=${Uri.encode(p.path)}")
            }
            "trojan://${Uri.encode(p.secret)}@${p.host}:${p.port}?$q#${Uri.encode(p.name)}"
        }
        "shadowsocks" -> {
            val userinfo = Uri.encode("${p.encryption}:${p.secret}")
            "ss://$userinfo@${p.host}:${p.port}#${Uri.encode(p.name)}"
        }
        "vmess" -> {
            val payload = buildString {
                append("{\"v\":\"2\",\"ps\":\"${p.name.jsonEscape()}\",\"add\":\"${p.host.jsonEscape()}\",\"port\":\"${p.port}\"")
                append(",\"id\":\"${p.secret.jsonEscape()}\",\"scy\":\"${p.encryption.ifBlank { "auto" }.jsonEscape()}\"")
                append(",\"net\":\"${p.network}\",\"type\":\"none\",\"host\":\"${p.hostHeader.jsonEscape()}\"")
                append(",\"path\":\"${p.path.jsonEscape()}\",\"tls\":\"${if (p.security == "tls") "tls" else ""}\"")
                append(",\"sni\":\"${p.sni.jsonEscape()}\",\"fp\":\"${p.fingerprint.jsonEscape()}\"}")
            }
            "vmess://" + Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        }
        else -> ""
    }

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    // ------------------------------------------------------------------ base64

    fun decodeBase64Lenient(s0: String): String? {
        var s = s0.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (s.isEmpty()) return null
        runCatching { return String(Base64.getMimeDecoder().decode(s), Charsets.UTF_8) }
        runCatching { return String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8) }
        if (s.endsWith("=")) {
            s = s.trimEnd('=')
            runCatching { return String(Base64.getMimeDecoder().decode(s), Charsets.UTF_8) }
        }
        return null
    }
}
