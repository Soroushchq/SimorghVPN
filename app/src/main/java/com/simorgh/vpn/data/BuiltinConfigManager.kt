package com.simorgh.vpn.data

import android.content.Context
import com.simorgh.vpn.R
import com.simorgh.vpn.ServiceLocator
import com.simorgh.vpn.util.LogBuffer

/**
 * Keeps the built-in free-config subscription alive (Derafsh pattern):
 *
 * 1. On every app start, make sure the built-in subscription exists and points
 *    at the active source.
 * 2. First launch (or empty built-in pool): import the snapshot bundled in the
 *    APK so the app works offline out of the box.
 * 3. If the last successful refresh is older than [BuiltInSources.REFRESH_INTERVAL_MS],
 *    refresh in the background (best-effort — on failure the previous servers stay).
 */
object BuiltinConfigManager {

    private const val TAG = "BuiltinConfigs"

    suspend fun onAppStart(context: Context) {
        val app = context.applicationContext
        val index = ServiceLocator.settings.value.builtinSourceIndex
        val url = BuiltInSources.sourceUrl(index)
        val name = sourceName(app, index)

        // 1) ensure the subscription exists (create or re-point at active source)
        val sub = ServiceLocator.profileRepo.ensureSubscription(
            BuiltInSources.BUILTIN_SUB_ID, name, url
        )

        // 2) offline seed when the pool is empty (first launch)
        val seeded = ServiceLocator.subscriptionRepo.seedBuiltinFromAssetIfEmpty(url)
        if (seeded > 0) {
            LogBuffer.i(TAG, "seeded $seeded servers from bundled snapshot")
        }

        // 3) auto-refresh when stale
        val stale = System.currentTimeMillis() - sub.lastUpdateAt >
            BuiltInSources.REFRESH_INTERVAL_MS
        if (stale) {
            runCatching { ServiceLocator.subscriptionRepo.refreshBuiltin(url) }
                .onSuccess { LogBuffer.i(TAG, "auto-refreshed $it servers from source ${index + 1}") }
                .onFailure { LogBuffer.w(TAG, "auto-refresh failed (keeping old servers): ${it.message}") }
        }
    }

    /** Switch the active source and refresh immediately. Returns server count. */
    suspend fun switchSource(context: Context, newIndex: Int): Int {
        val app = context.applicationContext
        SettingsStore.setBuiltinSourceIndex(app, newIndex)
        val url = BuiltInSources.sourceUrl(newIndex)
        val name = sourceName(app, newIndex)
        ServiceLocator.profileRepo.ensureSubscription(BuiltInSources.BUILTIN_SUB_ID, name, url)
        val n = ServiceLocator.subscriptionRepo.refreshBuiltin(url)
        LogBuffer.i(TAG, "switched to source ${newIndex + 1} ($n servers)")
        return n
    }

    fun sourceName(context: Context, index: Int): String =
        context.getString(R.string.source_name, index + 1)
}
