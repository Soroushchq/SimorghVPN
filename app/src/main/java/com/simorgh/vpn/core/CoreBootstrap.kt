package com.simorgh.vpn.core

import android.content.Context
import com.simorgh.vpn.util.LogBuffer
import go.Seq
import libv2ray.Libv2ray
import java.io.File
import java.util.UUID

/**
 * Boots the Xray core environment (same lifecycle as Derafsh/v2rayNG):
 * 1. extract geo database assets shipped inside libv2ray.aar into filesDir
 * 2. Seq.setContext + Libv2ray.initCoreEnv(filesDir, deviceId)
 */
object CoreBootstrap {

    @Volatile
    private var initialized = false

    val isInitialized: Boolean get() = initialized

    @Synchronized
    fun ensureInit(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        copyGeoAssets(app)
        Seq.setContext(app)
        Libv2ray.initCoreEnv(app.filesDir.absolutePath, deviceId(app))
        initialized = true
        LogBuffer.i("Core", "Xray core ready: ${version()}")
    }

    fun version(): String = runCatching { Libv2ray.checkVersionX() }.getOrDefault("n/a")

    private fun copyGeoAssets(app: Context) {
        listOf("geoip.dat", "geosite.dat").forEach { name ->
            val out = File(app.filesDir, name)
            if (!out.exists()) {
                app.assets.open(name).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                LogBuffer.i("Core", "extracted $name (${out.length()} bytes)")
            }
        }
    }

    private fun deviceId(app: Context): String {
        val sp = app.getSharedPreferences("simorgh_core", Context.MODE_PRIVATE)
        var id = sp.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            sp.edit().putString("device_id", id).apply()
        }
        return id
    }
}
