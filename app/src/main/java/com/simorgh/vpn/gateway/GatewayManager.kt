package com.simorgh.vpn.gateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.simorgh.vpn.data.SimorghSettings
import com.simorgh.vpn.util.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle of the sharing gateway (nova pattern): owns the proxy and the
 * portal, re-detects sharing interfaces whenever the system networks change
 * (hotspot on/off, USB tethering plug) and rebinds the listeners.
 *
 * The gateway is deliberately INDEPENDENT of the tunnel state: listeners stay
 * up while sharing is enabled — when the tunnel is down clients get a clear
 * 503/0x02 instead of "the internet disappeared", and everything heals by
 * itself the moment the tunnel returns.
 */
object GatewayManager {

    data class GatewayState(
        val running: Boolean = false,
        /** Sharing-side IPv4 addresses the proxy+portal listen on. */
        val addresses: List<String> = emptyList(),
        val ifaces: List<String> = emptyList()
    ) {
        fun portalUrls(): List<String> = addresses.map { "http://$it:${GatewayEndpoints.PORTAL_PORT}" }
        fun proxyEndpoints(): List<String> = addresses.map { "$it:${GatewayEndpoints.PROXY_PORT}" }
    }

    private val _state = MutableStateFlow(GatewayState())
    val state: StateFlow<GatewayState> = _state

    private var proxy: GatewayProxy? = null
    private var portal: GatewayPortal? = null
    private var appContext: Context? = null
    private var settingsRef: () -> SimorghSettings = { SimorghSettings() }
    private var tunnelAlive: () -> Boolean = { false }
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val started = AtomicBoolean(false)
    private val rebinding = AtomicBoolean(false)

    fun start(
        context: Context,
        settings: () -> SimorghSettings,
        tunnelAlive: () -> Boolean
    ) {
        if (!started.compareAndSet(false, true)) return
        appContext = context.applicationContext
        settingsRef = settings
        this.tunnelAlive = tunnelAlive

        val p = GatewayProxy(
            socksPort = { settingsRef().socksPort },
            tunnelAlive = tunnelAlive,
            creds = { settingsRef().gatewayUser to settingsRef().gatewayPass },
            noAuthSharing = { settingsRef().gatewayNoAuth },
            directWhenDown = { settingsRef().gatewayDirectWhenDown },
            sharingAddresses = { GatewayEndpoints.sharingAddresses(currentIfaces()) }
        )
        val portal = GatewayPortal(
            creds = { settingsRef().gatewayUser to settingsRef().gatewayPass },
            sharingAddresses = { GatewayEndpoints.sharingAddresses(currentIfaces()) }
        )
        proxy = p
        this.portal = portal

        registerNetworkCallback()
        rebind()
        LogBuffer.i("Gateway", "sharing gateway enabled")
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        unregisterNetworkCallback()
        runCatching { proxy?.stop() }
        runCatching { portal?.stop() }
        proxy = null
        portal = null
        _state.value = GatewayState()
        LogBuffer.i("Gateway", "sharing gateway disabled")
    }

    val running: Boolean get() = _state.value.running

    // ------------------------------------------------------------------ rebinding

    private fun registerNetworkCallback() {
        val ctx = appContext ?: return
        val cm = runCatching {
            ctx.getSystemService(ConnectivityManager::class.java)
        }.getOrNull() ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = scheduleRebind()
            override fun onLost(network: Network) = scheduleRebind()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = scheduleRebind()
        }
        runCatching { cm.registerNetworkCallback(android.net.NetworkRequest.Builder().build(), cb) }
            .onFailure { LogBuffer.w("Gateway", "network callback failed: ${it.message}") }
        callback = cb
    }

    private fun unregisterNetworkCallback() {
        val ctx = appContext ?: return
        val cb = callback ?: return
        runCatching {
            ctx.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
        }
        callback = null
    }

    /** Networks flap in bursts (e.g. hotspot toggle) — rebind once per burst. */
    private fun scheduleRebind() {
        if (!started.get()) return
        if (!rebinding.compareAndSet(false, true)) return
        Thread {
            try {
                Thread.sleep(1_000)
            } catch (_: InterruptedException) {
            }
            rebinding.set(false)
            runCatching { rebind() }
        }.apply { isDaemon = true }.start()
    }

    private fun rebind() {
        if (!started.get()) return
        val ctx = appContext ?: return
        val ifaces = GatewayEndpoints.detect(ctx)
        cachedIfaces = ifaces
        val addresses = GatewayEndpoints.sharingAddresses(ifaces).toList()

        if (addresses.isEmpty()) {
            runCatching { proxy?.stop() }
            runCatching { portal?.stop() }
            if (_state.value.running) {
                _state.value = GatewayState(running = false)
                LogBuffer.i("Gateway", "no sharing interface — listeners closed (hotspot off?)")
            }
            return
        }

        val pOk = proxy?.start(addresses) ?: false
        val portalOk = portal?.start(addresses) ?: false
        _state.value = GatewayState(
            running = pOk || portalOk,
            addresses = addresses,
            ifaces = ifaces.map { it.name }
        )
    }

    @Volatile
    private var cachedIfaces: List<GatewayEndpoints.SharingIface> = emptyList()

    /** Cheap answer for per-connection auth checks; refreshed on every rebind. */
    private fun currentIfaces(): List<GatewayEndpoints.SharingIface> = cachedIfaces

    private var lastRebindAt = 0L

    /** Periodic refresh hook (called from the daemon's 3s loop). */
    fun maybeRebindEvery(ms: Long = 15_000) {
        if (!started.get()) return
        val now = System.currentTimeMillis()
        if (now - lastRebindAt < ms) return
        lastRebindAt = now
        rebind()
    }
}
