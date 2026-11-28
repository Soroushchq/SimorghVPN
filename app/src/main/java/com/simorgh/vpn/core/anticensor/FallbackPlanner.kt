package com.simorgh.vpn.core.anticensor

import com.simorgh.vpn.data.Profile
import com.simorgh.vpn.data.SimorghSettings

/**
 * Builds the ordered connect-attempt plan for one "Connect" press
 * (nova/msn-guard fallback-chain pattern).
 *
 * One attempt = profile × uTLS fingerprint:
 *  - primary profile, fingerprints = rotation cycle (or the profile's own
 *    fixed fingerprint when rotation is off)
 *  - then fallback profiles ordered by last known latency (never-tested
 *    next, known-timeouts last), skipping the primary, capped
 *
 * With every anti-censor switch off the plan degenerates to exactly one
 * attempt — byte-for-byte the v0.2 behavior.
 */
object FallbackPlanner {

    /** Max fingerprint attempts per profile (out of [FingerprintRotator.CANDIDATES]). */
    const val MAX_FINGERPRINT_ATTEMPTS = 3

    /** Max profiles tried per connect (primary + fallbacks). */
    const val MAX_PROFILES = 3

    data class Attempt(
        val profile: Profile,
        /** blank = keep the profile's own fingerprint */
        val fingerprint: String,
        val fingerprintIndex: Int = -1,
        val isFallback: Boolean
    ) {
        val label: String
            get() {
                val fp = fingerprint.ifBlank { "profile-default" }
                return "${profile.name} · fp=$fp" + if (isFallback) " (fallback)" else ""
            }
    }

    fun plan(primary: Profile, all: List<Profile>, s: SimorghSettings): List<Attempt> {
        // --- no anti-censor: single attempt, exact v0.2 path
        if (!s.utlsRotation && !s.fallbackChain) {
            return listOf(Attempt(primary, "", isFallback = false))
        }

        val out = mutableListOf<Attempt>()

        fun pushProfile(p: Profile, fallback: Boolean) {
            if (!p.usesTls || !s.utlsRotation) {
                // rotation is meaningless without TLS/REALITY — one attempt
                out += Attempt(p, "", isFallback = fallback)
                return
            }
            FingerprintRotator.cycle(MAX_FINGERPRINT_ATTEMPTS).forEach { (fp, idx) ->
                out += Attempt(p, fp, fingerprintIndex = idx, isFallback = fallback)
            }
        }

        pushProfile(primary, fallback = false)

        if (s.fallbackChain) {
            val fallbacks = all.asSequence()
                .filter { it.id != primary.id }
                .sortedWith(
                    compareBy<Profile> { latencyRank(it.lastLatencyMs) }
                        .thenBy { if (it.lastLatencyMs >= 0) it.lastLatencyMs else Long.MAX_VALUE }
                        .thenBy { it.name } // deterministic order for equal candidates
                )
                .take((MAX_PROFILES - 1).coerceAtLeast(0))
            fallbacks.forEach { pushProfile(it, fallback = true) }
        }

        return out
    }

    /** Known-good latency first, never-tested next, known-timeouts last. */
    private fun latencyRank(latencyMs: Long): Int = when {
        latencyMs >= 0 -> 0
        latencyMs == -2L -> 1
        else -> 2
    }

    private val Profile.usesTls: Boolean
        get() = security == "tls" || security == "reality"
}
