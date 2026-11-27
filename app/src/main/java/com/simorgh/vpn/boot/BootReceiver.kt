package com.simorgh.vpn.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.simorgh.vpn.ServiceLocator
import com.simorgh.vpn.data.SettingsStore
import com.simorgh.vpn.util.LogBuffer
import com.simorgh.vpn.vpn.SimorghVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Auto-connect after boot / app update (Derafsh/V2rayNG BootReceiver pattern).
 * Only starts when the user enabled it in settings, a server exists and the
 * VPN permission was previously granted (never prompts from background).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext
                val settings = withTimeoutOrNull(3000) { SettingsStore.snapshot(app) }
                if (settings == null || !settings.autoStartBoot) return@launch

                runCatching { ServiceLocator.profileRepo.load() }
                val profiles = ServiceLocator.profileRepo.profiles.value
                if (profiles.isEmpty()) return@launch

                val pid = settings.activeProfileId.ifBlank { profiles.first().id }

                // never prompt for VPN permission from the background
                if (VpnService.prepare(app) == null) {
                    LogBuffer.i("Boot", "auto-start after $action → ${profiles.firstOrNull { it.id == pid }?.name}")
                    SimorghVpnService.start(app, pid)
                } else {
                    LogBuffer.w("Boot", "VPN permission not granted; skipping auto-start")
                }
            } catch (t: Throwable) {
                LogBuffer.w("Boot", "auto-start failed: ${t.message}")
            } finally {
                result.finish()
            }
        }
    }
}
