package com.simorgh.vpn.core.mae

import android.content.Context
import com.simorgh.vpn.util.LogBuffer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistence for MAE decisions: a small JSON file in filesDir. Both processes
 * touch it (UI pins overrides / reads statuses, daemon builds routing rules),
 * so every read is a fresh file read and every write is atomic.
 */
object MaeStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(context: Context) =
        File(context.applicationContext.filesDir, "mae-state.json")

    fun load(context: Context): MaeState = runCatching {
        val f = file(context)
        if (f.exists()) json.decodeFromString<MaeState>(f.readText()) else MaeState()
    }.getOrElse {
        LogBuffer.w("Mae", "state corrupted — starting fresh: ${it.message}")
        MaeState()
    }

    fun stateOf(context: Context, id: String): MaeServiceState =
        load(context).services[id] ?: MaeServiceState()

    fun setOverride(context: Context, id: String, route: MaeRoute) {
        mutate(context) { s ->
            val cur = s.services[id] ?: MaeServiceState()
            s.copy(services = s.services + (id to cur.copy(override = route)))
        }
    }

    fun applyProbe(context: Context, id: String, directOk: Boolean, ms: Long) {
        mutate(context) { s ->
            val cur = s.services[id] ?: MaeServiceState()
            s.copy(
                services = s.services + (id to cur.copy(
                    directOk = directOk,
                    probeMs = ms,
                    lastProbeAt = System.currentTimeMillis()
                )),
                lastLearnAt = System.currentTimeMillis()
            )
        }
    }

    /** Forget all learned evidence; user pins stay. */
    fun resetLearning(context: Context) {
        mutate(context) { s ->
            MaeState(
                services = s.services.mapValues { it.value.copy(directOk = null, probeMs = -1L) }
            )
        }
    }

    private fun mutate(context: Context, block: (MaeState) -> MaeState) {
        runCatching {
            val next = block(load(context))
            val f = file(context)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(next))
            if (!tmp.renameTo(f)) {
                f.delete()
                check(tmp.renameTo(f)) { "mae state rename failed" }
            }
        }.onFailure { LogBuffer.w("Mae", "state write failed: ${it.message}") }
    }
}
