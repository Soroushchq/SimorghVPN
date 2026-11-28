package com.simorgh.vpn.core.flux

import kotlinx.serialization.Serializable

/**
 * FLUX network memory (mlmvpn pattern, adapted to Simorgh).
 *
 * What the tunnel remembers, per network ("wifi" / "cell") and per route
 * (profile id × uTLS fingerprint, or warp endpoint):
 *  - an EWMA of connect success (the last ~10 attempts dominate)
 *  - recent RTT samples (TCP / probe round-trips)
 *  - failure streaks, cooldowns and quarantine for dead routes
 *
 * Everything here is pure data + pure transforms ([FluxMemory]) so the logic
 * is testable and the daemon only wires it to files and sockets.
 */
@Serializable
data class FluxMetrics(
    /** Exponential weighted success rate, 0..1. Neutral start 0.5. */
    val successEwma: Double = 0.5,
    val okCount: Int = 0,
    val failCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastOkAt: Long = 0L,
    val lastFailAt: Long = 0L,
    /** Cooldown deadline (epoch ms). 0 = free to try. Quarantine = long cooldown. */
    val cooldownUntil: Long = 0L,
    val lastReason: String? = null,
    /** Last RTT samples in ms, newest last, capped at [FluxMemory.RTT_KEEP]. */
    val rtts: List<Long> = emptyList()
) {
    val medianRtt: Long?
        get() = if (rtts.isEmpty()) null else rtts.sorted()[rtts.size / 2]
}

@Serializable
data class FluxState(
    /** Key = "<net>|<routeId>" — see [FluxState.key]. */
    val metrics: Map<String, FluxMetrics> = emptyMap(),
    val savedAt: Long = 0L
) {
    companion object {
        fun key(net: String, routeId: String) = "$net|$routeId"
    }
}

/** Why a route attempt failed — recorded in memory, shown in logs. */
enum class FluxFailReason(val label: String) {
    DIAL("dial"),
    ENGINE_START("engine-start"),
    DATA_PLANE("data-plane"),
    OTHER("other");
}
