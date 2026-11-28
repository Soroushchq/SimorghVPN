package com.simorgh.vpn.core.warp

import kotlinx.serialization.Serializable

/**
 * One registered Cloudflare WARP device (the Aether "Identity Vault" record).
 *
 * The keypair is minted locally with [com.simorgh.vpn.core.crypto.Curve25519];
 * only the public key ever leaves the device during registration.
 */
@Serializable
data class WarpIdentity(
    /** Cloudflare device id (uuid) — informational. */
    val id: String,
    /** base64 X25519 private key — NEVER leaves the vault unencrypted. */
    val privateKey: String,
    val publicKey: String,
    /** Assigned tunnel addresses. */
    val v4: String,
    val v6: String,
    /** Account client id (hex) → the 3 reserved bytes WARP routing needs. */
    val clientIdHex: String,
    val createdAt: Long
) {
    /** [b1, b2, b3] for the Xray wireguard `reserved` field; empty when unparsable. */
    val reserved: List<Int>
        get() = runCatching {
            val hex = clientIdHex.take(6).padStart(6, '0')
            listOf(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16)
            )
        }.getOrDefault(emptyList())

    val shortId: String get() = id.take(8)
}
