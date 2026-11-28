package com.simorgh.vpn

import android.app.Application
import com.simorgh.vpn.core.CoreBootstrap
import com.simorgh.vpn.core.engine.EngineRegistry
import com.simorgh.vpn.data.BuiltinConfigManager
import com.simorgh.vpn.data.SettingsMirror
import com.simorgh.vpn.data.SettingsStore
import com.simorgh.vpn.util.LogBuffer
import com.simorgh.vpn.util.Processes
import com.simorgh.vpn.vpn.DaemonIpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SimorghApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        EngineRegistry.registerDefaults()
        ServiceLocator.init(this)

        if (Processes.isDaemon) {
            // :daemon — the VPN service process. Jetpack DataStore is
            // single-process by design: no settings flow and no built-in
            // seed writes here. Settings arrive through the SettingsMirror
            // file the main process keeps fresh; profiles are re-read from
            // disk before every connection.
            LogBuffer.i("App", "SimorghVPN ${BuildConfig.VERSION_NAME} daemon starting")
            appScope.launch {
                runCatching { ServiceLocator.profileRepo.load() }
                    .onFailure { LogBuffer.e("App", "daemon profile load failed", it) }
            }
            appScope.launch(Dispatchers.IO) {
                runCatching { CoreBootstrap.ensureInit(this@SimorghApp) }
                    .onFailure { LogBuffer.e("App", "core init failed", it) }
            }
            return
        }

        appScope.launch {
            runCatching { ServiceLocator.profileRepo.load() }
                .onFailure { LogBuffer.e("App", "profile load failed", it) }
            // built-in free configs: ensure + seed + auto-refresh (best-effort)
            runCatching { BuiltinConfigManager.onAppStart(this@SimorghApp) }
                .onFailure { LogBuffer.e("App", "builtin config seed failed", it) }
        }
        appScope.launch {
            runCatching {
                SettingsStore.flow(this@SimorghApp).collect {
                    ServiceLocator.settings.value = it
                    // keep the :daemon settings mirror fresh (atomic write)
                    SettingsMirror.write(this@SimorghApp, it)
                }
            }.onFailure { LogBuffer.e("App", "settings collect failed", it) }
        }
        // warm up the Xray core in background (geo assets + native init)
        appScope.launch(Dispatchers.IO) {
            runCatching { CoreBootstrap.ensureInit(this@SimorghApp) }
                .onFailure { LogBuffer.e("App", "core init failed", it) }
        }
        // mirror daemon VPN state + engine logs into this process
        DaemonIpc.bind(this)

        LogBuffer.i("App", "SimorghVPN ${BuildConfig.VERSION_NAME} starting")
    }
}
