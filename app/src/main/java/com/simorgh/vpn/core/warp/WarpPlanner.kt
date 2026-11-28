package com.simorgh.vpn.core.warp

import com.simorgh.vpn.core.anticensor.FallbackPlanner
import com.simorgh.vpn.data.Profile
import com.simorgh.vpn.data.SimorghSettings

/**
 * The WARP flavor of the connect-attempt plan (sibling of
 * [FallbackPlanner], same [FallbackPlanner.Attempt] currency so the daemon
 * loop stays untouched):
 *
 *  - rotation off → a single attempt on the user's port
 *  - rotation on  → up to [MAX_ATTEMPTS] attempts mixing anycast IPs and
 *    alternate WireGuard ports (the warp-plus "best endpoint" technique):
 *    when the regime throttles/blocks one ip:port pair, the next attempt
 *    lands on a completely different pair.
 */
object WarpPlanner {

    const val MAX_ATTEMPTS = 6

    /** Synthetic profile shown in UI/logs while the WARP engine is active. */
    fun synthProfile(ip: String, port: Int): Profile = Profile(
        id = "warp",
        name = "WARP $ip:$port",
        protocol = "warp",
        host = ip,
        port = port
    )

    fun plan(s: SimorghSettings): List<FallbackPlanner.Attempt> {
        val port = if (s.warpPort in 1..65535) s.warpPort else WarpEndpoints.DEFAULT_PORT
        val primaryIp = WarpEndpoints.anycastIps().first()

        if (!s.warpPortRotation) {
            return listOf(
                FallbackPlanner.Attempt(
                    profile = synthProfile(primaryIp, port),
                    fingerprint = "",
                    warpIp = primaryIp,
                    warpPort = port,
                    isFallback = false
                )
            )
        }

        val attempts = mutableListOf(
            FallbackPlanner.Attempt(
                profile = synthProfile(primaryIp, port),
                fingerprint = "",
                warpIp = primaryIp,
                warpPort = port,
                isFallback = false
            )
        )

        // rotate: fresh IP first, then fresh port — so attempt #2 differs from
        // #1 in both coordinates as early as possible
        val altPorts = WarpEndpoints.ports().filter { it != port }
        val altIps = WarpEndpoints.anycastIps().drop(1)
        var i = 0
        while (attempts.size < MAX_ATTEMPTS) {
            val ip = altIps[i % altIps.size]
            val p = altPorts[i % altPorts.size]
            attempts += FallbackPlanner.Attempt(
                profile = synthProfile(ip, p),
                fingerprint = "",
                warpIp = ip,
                warpPort = p,
                isFallback = true
            )
            i++
        }
        return attempts
    }
}
