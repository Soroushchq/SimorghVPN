package com.simorgh.vpn.core.anticensor

import kotlinx.serialization.Serializable

/**
 * TLS ClientHello fragmentation presets, Iran-DPI-tuned (msn-guard pattern:
 * split the ClientHello into small random chunks with small random delays so
 * the DPI never sees the SNI/fingerprint in one packet).
 *
 * Maps 1:1 onto Xray-core `streamSettings.sockopt.fragment`:
 *   { "packets": "tlshello", "length": "a-b", "interval": "c-d" }
 */
@Serializable
enum class TlsFragmentProfile(val lengthSpec: String, val intervalSpec: String) {
    /** Soft: 40–100 byte chunks every 10–20 ms — near-zero overhead, beats most SNI sniffers. */
    IRAN_LIGHT("40-100", "10-20"),

    /** Medium: 10–50 byte chunks every 5–15 ms — heavier confusion, still fine on 3G/4G. */
    IRAN_MEDIUM("10-50", "5-15"),

    /** Aggressive: 1–10 byte chunks every 5–10 ms — msn-guard style micro-chunks for hostile networks. */
    IRAN_AGGRESSIVE("1-10", "5-10");

    companion object {
        fun of(raw: String?): TlsFragmentProfile =
            entries.firstOrNull { it.name == raw } ?: IRAN_LIGHT
    }
}
