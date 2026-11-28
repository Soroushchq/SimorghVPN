package com.simorgh.vpn.core.engine

import android.content.Context
import android.os.ParcelFileDescriptor
import com.simorgh.vpn.core.CoreBootstrap
import com.simorgh.vpn.core.XrayConfigBuilder
import com.simorgh.vpn.core.XrayRunner
import com.simorgh.vpn.core.mae.MaeRules
import com.simorgh.vpn.core.mae.MaeStore
import com.simorgh.vpn.data.Profile
import com.simorgh.vpn.data.SimorghSettings
import com.simorgh.vpn.data.TunnelMode
import com.simorgh.vpn.util.LogBuffer
import com.simorgh.vpn.vpn.HevTunnel
import kotlinx.coroutines.delay
import java.io.File

/**
 * The Xray engine: libv2ray.aar core (config generation + native loop) plus
 * the hev-socks5-tunnel bridge in HEV mode. Exactly the v0.1/v0.2 data path,
 * now behind the [VpnEngine] seam.
 */
class XrayEngine : VpnEngine {

    override val id: String = EngineRegistry.XRAY

    private var runner: XrayRunner? = null

    override val isRunning: Boolean
        get() = runner?.isRunning ?: false

    override suspend fun start(
        context: Context,
        profile: Profile,
        settings: SimorghSettings,
        tun: ParcelFileDescriptor?
    ): Boolean {
        val app = context.applicationContext
        CoreBootstrap.ensureInit(app)

        val logPath = File(app.filesDir, "simorgh-xray.log").absolutePath
        // v0.4 MAE: per-service adaptive routing rules (fresh file read — the
        // daemon and the UI both mutate the store)
        val maeRoutes = if (settings.maeEnabled) {
            MaeRules.build(MaeStore.load(app))
        } else emptyList()
        val config = XrayConfigBuilder.build(profile, settings, logPath, maeRoutes)
        LogBuffer.d("Vpn", "config generated (${config.length} chars) mode=${settings.tunnelMode} mae=${maeRoutes.size}")

        val r = XrayRunner()
        val fd = if (settings.tunnelMode == TunnelMode.XRAY_TUN) (tun?.fd ?: 0) else 0
        if (!r.start(config, fd)) return false
        runner = r

        if (settings.tunnelMode == TunnelMode.HEV && tun != null) {
            HevTunnel.start(app, tun, settings)
            if (!HevTunnel.running) {
                // bridge down → the core alone would black-hole; treat as failure
                r.stop()
                runner = null
                return false
            }
        }
        return true
    }

    override fun stop() {
        HevTunnel.stop()
        runner?.stop()
    }

    override suspend fun stopAwait(timeoutMs: Long) {
        HevTunnel.stop()
        val r = runner ?: return
        r.stop()
        var waited = 0
        while (r.isRunning && waited < timeoutMs) {
            delay(150)
            waited += 150
        }
        r.release()
        runner = null
    }

    override fun stats(): Pair<Long, Long> = XrayRunner.parseStats(runner?.statsRaw())
}
