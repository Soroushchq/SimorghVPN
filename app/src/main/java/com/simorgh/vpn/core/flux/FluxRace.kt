package com.simorgh.vpn.core.flux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Stage-1 route race (mlmvpn FluxRacer pattern, TCP-reachability flavor):
 * before spending any real engine start, every candidate endpoint gets one
 * cheap parallel TCP connect. Survivors come back ordered by connect time;
 * unreachable endpoints sink to the end (they are NOT failed in memory — a
 * censor can fake a RST, only a full engine attempt writes a verdict).
 *
 * Bounded concurrency, one hard deadline, blocking connects confined to IO
 * threads (each connect also has its own socket timeout as a ceiling).
 */
object FluxRace {

    data class Endpoint(
        val host: String,
        val port: Int,
        /** All route ids that dial this endpoint. */
        val routeIds: List<String>
    ) {
        val id: String get() = "$host:$port"
    }

    data class Reach(val ok: Boolean, val ms: Long = -1L)

    /**
     * @return per-endpoint reachability; ordering of the returned map follows
     *         answer time (fastest survivors first, then failures).
     */
    suspend fun reachability(
        endpoints: List<Endpoint>,
        concurrency: Int = 12,
        deadlineMs: Long = 2_500,
        connectTimeoutMs: Int = 2_000
    ): Map<String, Reach> = withContext(Dispatchers.IO) {
        val distinct = endpoints.distinctBy { it.id }
        if (distinct.isEmpty()) return@withContext emptyMap()

        coroutineScope {
            val gate = Semaphore(concurrency.coerceAtLeast(1))
            val done = LinkedHashMap<String, Reach>()

            suspend fun probe(e: Endpoint): Pair<String, Reach> {
                val t0 = System.nanoTime()
                val ok = runCatching {
                    Socket().use { s ->
                        s.tcpNoDelay = true
                        s.connect(InetSocketAddress(e.host, e.port), connectTimeoutMs)
                        true
                    }
                }.getOrDefault(false)
                return e.id to Reach(ok, (System.nanoTime() - t0) / 1_000_000)
            }

            val results = kotlinx.coroutines.channels.Channel<Pair<String, Reach>>(distinct.size)
            distinct.forEach { e ->
                launch {
                    gate.withPermit { results.send(probe(e)) }
                }
            }

            var received = 0
            val t0 = System.nanoTime()
            while (received < distinct.size) {
                val left = deadlineMs - (System.nanoTime() - t0) / 1_000_000
                if (left <= 0) break
                val next = withTimeoutOrNull(left) { results.receive() } ?: break
                received++
                done[next.first] = next.second
            }
            // late / never-answered endpoints (deadline hit) count as failures.
            // The channel is deliberately NOT closed: late probes may still send
            // into the unbounded buffer — a closed channel would throw in their
            // coroutine and fail the whole race scope.
            distinct.forEach { if (it.id !in done) done[it.id] = Reach(false) }

            // survivors first (by ms), then failures — insertion order does it
            done.entries.sortedWith(
                compareBy({ !it.value.ok }, { if (it.value.ok) it.value.ms else Long.MAX_VALUE })
            ).associate { it.toPair() }
        }
    }
}
