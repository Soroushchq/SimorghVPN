package com.simorgh.vpn

import android.app.Application
import com.simorgh.vpn.core.CoreBootstrap
import com.simorgh.vpn.data.SettingsStore
import com.simorgh.vpn.util.LogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SimorghApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        appScope.launch {
            runCatching { ServiceLocator.profileRepo.load() }
                .onFailure { LogBuffer.e("App", "profile load failed", it) }
        }
        appScope.launch {
            runCatching {
                SettingsStore.flow(this@SimorghApp).collect { ServiceLocator.settings.value = it }
            }.onFailure { LogBuffer.e("App", "settings collect failed", it) }
        }
        // warm up the Xray core in background (geo assets + native init)
        appScope.launch(Dispatchers.IO) {
            runCatching { CoreBootstrap.ensureInit(this@SimorghApp) }
                .onFailure { LogBuffer.e("App", "core init failed", it) }
        }

        LogBuffer.i("App", "SimorghVPN ${BuildConfig.VERSION_NAME} starting")
    }
}
