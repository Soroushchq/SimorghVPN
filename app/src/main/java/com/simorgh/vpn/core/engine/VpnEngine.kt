package com.simorgh.vpn.core.engine

import android.content.Context
import android.os.ParcelFileDescriptor
import com.simorgh.vpn.data.Profile
import com.simorgh.vpn.data.SimorghSettings

/**
 * A tunnel engine (mlmvpn IVpnEngine pattern).
 *
 * v0.2.1 ships a single implementation — Xray via libv2ray.aar with the
 * hev-socks5-tunnel bridge — behind this seam. v0.3+ engines (Aether MASQUE,
 * WARP, ...) register into [EngineRegistry] and the service code does not
 * change.
 */
interface VpnEngine {

    /** Registry id, e.g. "xray". Persisted in settings as `engineId`. */
    val id: String

    /** True while the native core loop is alive. */
    val isRunning: Boolean

    /**
     * Boot the engine for [profile].
     *
     * @param tun the established VPN interface, or null in proxy-only mode;
     *   engines that own their TUN stack consume its fd directly, while
     *   tun2socks-style engines bridge it to their local proxy port.
     * @return true when the engine reached a running state
     */
    suspend fun start(
        context: Context,
        profile: Profile,
        settings: SimorghSettings,
        tun: ParcelFileDescriptor?
    ): Boolean

    /** Fire-and-forget stop (lifecycle teardown paths). */
    fun stop()

    /** Stop and wait (bounded) until the core settles; releases resources. */
    suspend fun stopAwait(timeoutMs: Long = 3000)

    /** Current (up, down) byte totals through the proxy; 0 to 0 when unknown. */
    fun stats(): Pair<Long, Long>
}
