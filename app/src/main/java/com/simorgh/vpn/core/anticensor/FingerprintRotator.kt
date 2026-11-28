package com.simorgh.vpn.core.anticensor

import com.simorgh.vpn.util.LogBuffer

/**
 * uTLS fingerprint rotation (nova pattern).
 *
 * When a TLS/REALITY connection dies at the handshake, the censor either
 * blocked the *fingerprint* or the *SNI/domain*. Instead of guessing, we
 * re-dial with a different browser fingerprint: if a later fingerprint
 * connects, the previous one was fingerprint-blocked; if none do, the
 * endpoint itself is dead — that is the whole "name or fingerprint" test.
 *
 * TLS1.3-only fingerprints (android/qq/360 per nova's compatibility list)
 * are excluded — REALITY needs TLS1.2-compatible hello layers.
 */
object FingerprintRotator {

    val CANDIDATES = listOf(
        "chrome",     // the safe default everywhere
        "firefox",
        "safari",
        "edge",
        "ios",
        "android",
        "randomized"  // Xray's own per-connection random pick
    )

    /**
     * Ordered candidate list for this connect, starting after [lastIndex].
     * Rotation survives within the daemon process across reconnects, so the
     * next connect starts where the last successful one ended.
     */
    @Volatile
    private var lastGoodIndex: Int = 0

    fun nextIndex(): Int = (lastGoodIndex + 1).mod(CANDIDATES.size)

    fun rememberGood(index: Int) {
        lastGoodIndex = index.mod(CANDIDATES.size)
        LogBuffer.i("AntiCensor", "uTLS fingerprint '${CANDIDATES[lastGoodIndex]}' worked — sticky for next connects")
    }

    /**
     * Attempt list for one connect: rotate-first ordering, e.g. after chrome
     * held the slot → [firefox, safari, edge, ios, android, randomized, chrome].
     */
    fun cycle(count: Int): List<Pair<String, Int>> {
        val n = CANDIDATES.size
        val start = nextIndex()
        return (0 until count.coerceAtMost(n)).map { i ->
            val idx = (start + i).mod(n)
            CANDIDATES[idx] to idx
        }
    }
}
