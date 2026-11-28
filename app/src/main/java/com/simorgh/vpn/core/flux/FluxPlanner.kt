package com.simorgh.vpn.core.flux

import android.content.Context
import com.simorgh.vpn.core.anticensor.FallbackPlanner
import com.simorgh.vpn.util.LogBuffer

/**
 * FLUX ordering (mlmvpn planner pattern): takes the connect plan built by
 * [FallbackPlanner]/WarpPlanner and reorders it with everything the tunnel
 * knows before a single engine is started:
 *
 *  1. network memory — routes proven on THIS network class come first,
 *     cooling-down and quarantined routes sink to the end
 *  2. a live TCP race — endpoints that answer fastest go first; endpoints
 *     that do not answer at all go last (but are still tried: a fake RST
 *     must not kill a route forever)
 *
 * With a single attempt this is a no-op.
 */
object FluxPlanner {

    /** Stable route identity: profile × fingerprint, or a warp endpoint. */
    fun routeKey(a: FallbackPlanner.Attempt): String = when {
        a.warpIp.isNotBlank() -> "warp:${a.warpIp}:${a.warpPort}"
        a.fingerprint.isNotBlank() -> "${a.profile.id}|fp:${a.fingerprint}"
        else -> a.profile.id
    }

    suspend fun order(
        context: Context,
        attempts: List<FallbackPlanner.Attempt>
    ): List<FallbackPlanner.Attempt> {
        if (attempts.size <= 1) return attempts

        FluxStore.load(context)
        val net = FluxNet.currentKey(context)
        val state = FluxStore.snapshot()
        val now = System.currentTimeMillis()

        // ---- 1) memory ranking
        val byRoute = attempts.groupBy { routeKey(it) }
        val rankedRoutes = FluxMemory.rank(state, net, byRoute.keys.toList(), now)

        // ---- 2) live TCP race over distinct endpoints
        val endpoints = attempts.mapNotNull { a ->
            if (a.warpIp.isNotBlank()) {
                FluxRace.Endpoint(a.warpIp, a.warpPort, listOf(routeKey(a)))
            } else if (a.profile.host.isNotBlank() && a.profile.port in 1..65535) {
                FluxRace.Endpoint(a.profile.host, a.profile.port, listOf(routeKey(a)))
            } else null
        }.let { list ->
            // merge endpoints with the same host:port (same profile, different fingerprints)
            list.groupBy { it.id }.map { (_, es) ->
                FluxRace.Endpoint(es.first().host, es.first().port, es.flatMap { it.routeIds })
            }
        }

        val reach = if (endpoints.size > 1) {
            FluxRace.reachability(endpoints)
        } else emptyMap()

        // endpoint-level RTT of each route's dial address (null = not raced or unreachable)
        fun routeMs(routeId: String): Long? {
            endpoints.forEach { e ->
                if (routeId in e.routeIds) {
                    val r = reach[e.id]
                    return if (r != null && r.ok) r.ms else null
                }
            }
            return null
        }

        // ---- 3) compose: ranked routes → race-ok first (by ms), race-fail last
        val out = mutableListOf<FallbackPlanner.Attempt>()
        val raceWinners = rankedRoutes.filter { rid -> routeMs(rid) != null }
        val raceLosers = rankedRoutes.filter { rid -> routeMs(rid) == null && reach.isNotEmpty() }
        val unraced = rankedRoutes.filter { rid -> reach.isEmpty() || endpoints.none { rid in it.routeIds } }
        (raceWinners.sortedBy { routeMs(it)!! } + unraced + raceLosers).forEach { rid ->
            out += byRoute[rid].orEmpty()
        }
        // safety: anything dropped by grouping goes back unchanged
        if (out.size != attempts.size) return attempts

        val cooled = rankedRoutes.count { FluxMemory.coolingDown(state, net, it, now) }
        val raced = reach.count { it.value.ok }
        LogBuffer.i(
            "Flux",
            "plan ordered: ${attempts.size} attempts, ${raceWinners.size} raced-ok, $cooled cooling (net=$net)"
        )
        return out
    }
}
