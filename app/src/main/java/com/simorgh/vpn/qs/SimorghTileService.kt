package com.simorgh.vpn.qs

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.simorgh.vpn.MainActivity
import com.simorgh.vpn.ServiceLocator
import com.simorgh.vpn.core.VpnBus
import com.simorgh.vpn.core.VpnStatus
import com.simorgh.vpn.data.SettingsStore
import com.simorgh.vpn.vpn.SimorghVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Quick-settings tile: one-tap connect/disconnect (Derafsh QSTileService
 * pattern). Connect uses the active (or first) server; if VPN permission was
 * never granted, it opens the app instead of failing silently.
 */
class SimorghTileService : TileService() {

    private var observeJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
        observeJob = CoroutineScope(Dispatchers.Default).launch {
            VpnBus.state.collect { refreshTile() }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        observeJob?.cancel()
        observeJob = null
    }

    override fun onClick() {
        super.onClick()
        val status = VpnBus.state.value.status
        when (status) {
            VpnStatus.CONNECTED, VpnStatus.CONNECTING, VpnStatus.BLOCKED, VpnStatus.STOPPING -> {
                SimorghVpnService.stop(this)
            }
            else -> {
                if (VpnService.prepare(this) != null) {
                    // no permission yet → open the app
                    openApp()
                    return
                }
                val pid = runBlocking {
                    withTimeoutOrNull(2500) {
                        runCatching { ServiceLocator.profileRepo.load() }
                        val settings = SettingsStore.snapshot(this@SimorghTileService)
                        if (settings.engineId == com.simorgh.vpn.core.engine.EngineRegistry.WARP) {
                            "" // serverless — daemon resolves the identity itself
                        } else {
                            val profiles = ServiceLocator.profileRepo.profiles.value
                            settings.activeProfileId.ifBlank { profiles.firstOrNull()?.id }
                        }
                    }
                }
                if (pid != null) {
                    SimorghVpnService.start(this, pid)
                } else {
                    openApp() // no servers configured → open the app
                }
            }
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile() {
        val t = qsTile ?: return
        val state = VpnBus.state.value
        t.state = when (state.status) {
            VpnStatus.CONNECTED, VpnStatus.CONNECTING -> Tile.STATE_ACTIVE
            VpnStatus.BLOCKED -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        t.updateTile()
    }
}
