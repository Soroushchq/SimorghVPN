package com.simorgh.vpn.core.engine

import android.content.Context
import android.os.ParcelFileDescriptor
import com.simorgh.vpn.core.CoreBootstrap
import com.simorgh.vpn.core.warp.WarpConfigBuilder
import com.simorgh.vpn.core.warp.WarpIdentityVault
import com.simorgh.vpn.core.XrayRunner
import com.simorgh.vpn.data.Profile
import com.simorgh.vpn.data.SimorghSettings
import com.simorgh.vpn.data.TunnelMode
import com.simorgh.vpn.util.LogBuffer
import com.simorgh.vpn.vpn.HevTunnel
import kotlinx.coroutines.delay
import java.io.File

/**
 * The WARP engine (v0.3.1): Xray's native `wireguard` outbound carrying an
 * Identity Vault device = a free, serverless Cloudflare tunnel. The tunnel
 * chassis (SOCKS inbound, HEV bridge, stats) is byte-for-byte the Xray
 * engine's — only the outbound differs.
 */
class WarpEngine : VpnEngine {

    override val id: String = EngineRegistry.WARP

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

        val identity = WarpIdentityVault.active(app)
        if (identity == null) {
            LogBuffer.e("Warp", "no identity in vault — register one first (Settings → WARP)")
            return false
        }

        val logPath = File(app.filesDir, "simorgh-xray.log").absolutePath
        val config = WarpConfigBuilder.build(identity, settings, logPath)
        LogBuffer.d(
            "Warp",
            "config generated (${config.length} chars) device=${identity.shortId} mode=${settings.tunnelMode}"
        )

        val r = XrayRunner()
        val fd = if (settings.tunnelMode == TunnelMode.XRAY_TUN) (tun?.fd ?: 0) else 0
        if (!r.start(config, fd)) return false
        runner = r

        if (settings.tunnelMode == TunnelMode.HEV && tun != null) {
            HevTunnel.start(app, tun, settings)
            if (!HevTunnel.running) {
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
