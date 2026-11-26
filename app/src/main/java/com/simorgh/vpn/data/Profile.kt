package com.simorgh.vpn.data

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val name: String,
    /** vless | vmess | trojan | shadowsocks */
    val protocol: String,
    val host: String,
    val port: Int,
    /** uuid (vless/vmess) or password (trojan/ss) */
    val secret: String = "",
    /** vless: none; vmess: auto/aes-128-gcm/chacha20-poly1305; ss: method */
    val encryption: String = "",
    /** e.g. xtls-rprx-vision */
    val flow: String = "",
    /** tcp | ws | grpc | httpupgrade | xhttp */
    val network: String = "tcp",
    /** none | tls | reality */
    val security: String = "none",
    val sni: String = "",
    val hostHeader: String = "",
    val path: String = "",
    val serviceName: String = "",
    /** uTLS fingerprint, e.g. chrome */
    val fingerprint: String = "",
    /** REALITY public key */
    val publicKey: String = "",
    /** REALITY short id */
    val shortId: String = "",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val subscriptionId: String = "",
    val addedAt: Long = 0L,
    /** -2 = never tested, -1 = timeout, >=0 = latency ms */
    var lastLatencyMs: Long = -2L,
    var lastTestAt: Long = 0L
)

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val addedAt: Long = 0L,
    /** Epoch ms of the last successful refresh (0 = never). */
    val lastUpdateAt: Long = 0L
)
