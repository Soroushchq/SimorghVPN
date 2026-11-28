package com.simorgh.vpn.data

import android.content.Context
import com.simorgh.vpn.util.LogBuffer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-based settings mirror for the :daemon process. Jetpack DataStore is
 * single-process by design, so the main process writes this JSON snapshot on
 * every settings change and the daemon reads it. Writes are atomic
 * (tmp + rename) so a concurrent reader never sees a torn file.
 */
object SettingsMirror {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(context: Context) =
        File(context.applicationContext.filesDir, "daemon-settings.json")

    fun write(context: Context, s: SimorghSettings) {
        runCatching {
            val f = file(context)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(s))
            if (!tmp.renameTo(f)) {
                f.delete()
                check(tmp.renameTo(f)) { "atomic settings rename failed" }
            }
        }.onFailure { LogBuffer.w("Mirror", "settings write failed: ${it.message}") }
    }

    fun read(context: Context): SimorghSettings? =
        runCatching {
            json.decodeFromString<SimorghSettings>(file(context).readText())
        }.getOrNull()
}
