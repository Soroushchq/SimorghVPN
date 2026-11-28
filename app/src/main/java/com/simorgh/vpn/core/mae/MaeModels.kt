package com.simorgh.vpn.core.mae

import kotlinx.serialization.Serializable

/**
 * MAE per-service adaptive routing (mlmvpn pattern, Simorgh flavor).
 *
 * Instead of one global bypass rule, every well-known service gets its own
 * route decision learned by real probes: a service that answers directly
 * (its CDN works, it is merely "annoying" rather than blocked) goes DIRECT —
 * faster, saves tunnel bandwidth — while everything else stays on the proxy.
 *
 * The user can pin any service (PROXY / DIRECT); AUTO follows the learned
 * evidence.
 */
@Serializable
enum class MaeRoute { AUTO, PROXY, DIRECT }

@Serializable
data class MaeServiceState(
    val override: MaeRoute = MaeRoute.AUTO,
    /** null = never probed; true = answered directly; false = blocked/dead direct. */
    val directOk: Boolean? = null,
    val lastProbeAt: Long = 0L,
    /** Last direct probe round-trip in ms (-1 = failed). */
    val probeMs: Long = -1L
) {
    /** What the routing table should do for this service right now. */
    val effective: MaeRoute
        get() = when (override) {
            MaeRoute.AUTO -> when (directOk) {
                true -> MaeRoute.DIRECT
                false -> MaeRoute.PROXY
                null -> MaeRoute.AUTO // unknown → no MAE rule, default path
            }
            else -> override
        }
}

@Serializable
data class MaeState(
    val services: Map<String, MaeServiceState> = emptyMap(),
    val lastLearnAt: Long = 0L
)

/** One registry entry: what a service is and how to test it directly. */
data class MaeServiceDef(
    val id: String,
    /** Persian display name. */
    val faName: String,
    /** Registered domains — each also covers its subdomains (Xray suffix match). */
    val domains: List<String>,
    /** A tiny safe GET that proves the service answers. */
    val probeUrl: String,
    /** What is usually true in Iran — the cold-start prior (before evidence). */
    val likelyCensored: Boolean = true
)
