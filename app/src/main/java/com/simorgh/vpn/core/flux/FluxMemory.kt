package com.simorgh.vpn.core.flux

/**
 * Pure transformations of [FluxState] (a faithful port of mlmvpn's FluxMemory
 * to Simorgh's route keys). Every change to what the tunnel remembers about
 * routes goes through here.
 *
 * Behavior (mlmvpn semantics, tuned for phone use):
 *  - success: EWMA rises (α=0.2), failure streak resets, cooldown clears
 *  - failure: streak grows; at [BREAKER_THRESHOLD] the route cools down with
 *    an exponential backoff (60s doubling, capped at 6h)
 *  - a route that failed ≥8 times with an almost-dead EWMA is quarantined
 *    for 24h (dead public node — parked, not retried hourly)
 */
object FluxMemory {

    private const val ALPHA = 0.2
    const val BREAKER_THRESHOLD = 3
    private const val COOLDOWN_BASE_MS = 60_000L
    private const val COOLDOWN_MAX_MS = 6L * 3600_000L
    private const val QUARANTINE_MS = 24L * 3600_000L
    private const val QUARANTINE_FAILS = 8
    private const val RTT_KEEP = 8

    fun metrics(s: FluxState, net: String, routeId: String): FluxMetrics? =
        s.metrics[FluxState.key(net, routeId)]

    fun recordSuccess(
        s: FluxState, net: String, routeId: String, now: Long, rttMs: Long? = null
    ): FluxState {
        val key = FluxState.key(net, routeId)
        val m = s.metrics[key] ?: FluxMetrics()
        val next = m.copy(
            successEwma = m.successEwma * (1 - ALPHA) + ALPHA,
            rtts = if (rttMs == null) m.rtts else (m.rtts + rttMs).takeLast(RTT_KEEP),
            okCount = m.okCount + 1,
            consecutiveFailures = 0,
            lastOkAt = now,
            cooldownUntil = 0L,
            lastReason = null
        )
        return s.copy(metrics = s.metrics + (key to next))
    }

    fun recordFailure(
        s: FluxState, net: String, routeId: String, now: Long, reason: FluxFailReason
    ): FluxState {
        val key = FluxState.key(net, routeId)
        val m = s.metrics[key] ?: FluxMetrics()
        val streak = m.consecutiveFailures + 1
        val ewma = m.successEwma * (1 - ALPHA)
        val cooldown = when {
            // failed many times and almost never worked: a dead node — park it
            m.failCount + 1 >= QUARANTINE_FAILS && ewma < 0.1 -> now + QUARANTINE_MS
            streak >= BREAKER_THRESHOLD ->
                now + minOf(COOLDOWN_MAX_MS, COOLDOWN_BASE_MS shl minOf(16, streak - BREAKER_THRESHOLD))
            else -> 0L
        }
        val next = m.copy(
            successEwma = ewma,
            failCount = m.failCount + 1,
            consecutiveFailures = streak,
            lastFailAt = now,
            cooldownUntil = if (cooldown > m.cooldownUntil) cooldown else m.cooldownUntil,
            lastReason = reason.label
        )
        return s.copy(metrics = s.metrics + (key to next))
    }

    fun coolingDown(s: FluxState, net: String, routeId: String, now: Long): Boolean {
        val m = metrics(s, net, routeId) ?: return false
        return m.cooldownUntil != 0L && now < m.cooldownUntil
    }

    /**
     * Ranking score for an AVAILABLE (not cooling) route: proven success first,
     * fast routes next, a recent success is a small bonus, never-tried sits
     * slightly below a proven route and well above a failing one.
     */
    fun score(s: FluxState, net: String, routeId: String, now: Long): Double {
        val m = metrics(s, net, routeId)
            ?: return 45.0 // never tried: neutral-positive
        var v = m.successEwma * 100.0
        m.medianRtt?.let { v -= (it / 100.0).coerceAtMost(30.0) }
        if (m.lastOkAt > 0L && now - m.lastOkAt < 3600_000L) v += 10.0
        if (m.okCount == 0 && m.failCount > 0) v -= 15.0 // only failures so far
        return v
    }

    /**
     * Order route ids for the next connect: available routes by score (stable
     * for ties), then cooling routes at the end by their EWMA — so if EVERYTHING
     * is cooling we still try the historically best one.
     */
    fun rank(s: FluxState, net: String, routeIds: List<String>, now: Long): List<String> {
        val (free, cooling) = routeIds.distinct().partition { !coolingDown(s, net, it, now) }
        val freeRanked = free.sortedByDescending { score(s, net, it, now) }
        val coolingRanked = cooling.sortedByDescending {
            metrics(s, net, it)?.successEwma ?: 0.5
        }
        return freeRanked + coolingRanked
    }

    /** Drop memory for networks not seen for a month. */
    fun prune(s: FluxState, now: Long, maxAgeMs: Long = 30L * 24 * 3600_000L): FluxState {
        val staleNets = s.metrics.entries
            .groupBy { it.key.substringBefore('|') }
            .filterValues { ms -> ms.all { it.value.lastOkAt < now - maxAgeMs && it.value.lastFailAt < now - maxAgeMs } }
            .keys
        if (staleNets.isEmpty()) return s
        return s.copy(
            metrics = s.metrics.filterKeys { k -> k.substringBefore('|') !in staleNets }
        )
    }
}
