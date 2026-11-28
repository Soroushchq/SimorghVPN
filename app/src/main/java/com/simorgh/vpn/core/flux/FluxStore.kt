package com.simorgh.vpn.core.flux

import android.content.Context
import com.simorgh.vpn.util.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistence for the FLUX network memory: a small JSON file in filesDir,
 * read fresh and written atomically. Only the :daemon process mutates it;
 * every mutation is one bounded file rewrite (the state is tiny by design).
 */
object FluxStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    @Volatile
    private var state: FluxState = FluxState()

    private fun file(context: Context) =
        File(context.applicationContext.filesDir, "flux-memory.json")

    suspend fun load(context: Context) {
        mutex.withLock {
            val f = file(context)
            if (!f.exists()) {
                state = FluxState()
                return@withLock
            }
            state = runCatching { json.decodeFromString<FluxState>(f.readText()) }
                .getOrElse {
                    LogBuffer.w("Flux", "memory corrupted — starting fresh: ${it.message}")
                    FluxState()
                }
        }
    }

    fun snapshot(): FluxState = state

    suspend fun recordSuccess(
        context: Context, net: String, routeId: String, rttMs: Long? = null
    ) = mutate(context) { s ->
        FluxMemory.recordSuccess(s, net, routeId, System.currentTimeMillis(), rttMs)
    }

    suspend fun recordFailure(
        context: Context, net: String, routeId: String, reason: FluxFailReason
    ) = mutate(context) { s ->
        FluxMemory.recordFailure(s, net, routeId, System.currentTimeMillis(), reason)
    }

    /** Forget everything (future "reset" UI). */
    suspend fun clear(context: Context) = mutate(context) { FluxState() }

    private suspend fun mutate(context: Context, block: (FluxState) -> FluxState) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                var next = block(state)
                next = FluxMemory.prune(next, System.currentTimeMillis())
                state = next
                runCatching {
                    val f = file(context)
                    val tmp = File(f.parentFile, f.name + ".tmp")
                    tmp.writeText(json.encodeToString(next))
                    if (!tmp.renameTo(f)) {
                        f.delete()
                        check(tmp.renameTo(f)) { "flux memory rename failed" }
                    }
                }.onFailure { LogBuffer.w("Flux", "memory save failed: ${it.message}") }
            }
        }
    }
}
