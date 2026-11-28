package com.simorgh.vpn.core.mae

import com.simorgh.vpn.util.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The MAE learner: one tiny, safe GET per service, DIRECT (the app package is
 * excluded from the TUN, so its sockets always go direct), and the answer
 * decides the service's route — reachable ⇒ DIRECT (faster, no tunnel quota),
 * blocked ⇒ PROXY.
 *
 * "Reachable" means: a real HTTP response came back within the budget. A 4xx
 * (geo-block page, 403…) still proves the transport works — for the v1
 * decision that counts as reachable, because the traffic is not being
 * black-holed by the censor. Signature-based refinements (mlmvpn
 * geoSignatures) can build on this later.
 */
object MaeLearner {

    private const val TIMEOUT_MS = 5_000
    private const val GAP_MS = 250L
    private const val READ_CAP = 8 * 1024

    data class ProbeResult(val directOk: Boolean, val ms: Long)

    data class Summary(val okCount: Int, val blockedCount: Int)

    /**
     * Probe every registry service directly and store the verdicts.
     * @param onlyStale skip services probed within the last 12 hours.
     */
    suspend fun learn(context: android.content.Context, onlyStale: Boolean = true): Summary =
        withContext(Dispatchers.IO) {
            val state = MaeStore.load(context)
            val now = System.currentTimeMillis()
            var ok = 0
            var blocked = 0
            for (def in MaeRegistry.SERVICES) {
                val st = state.services[def.id]
                if (onlyStale && st != null && st.lastProbeAt > 0 &&
                    now - st.lastProbeAt < 12L * 3600_000L
                ) continue

                val r = probe(def)
                MaeStore.applyProbe(context, def.id, r.directOk, r.ms)
                if (r.directOk) ok++ else blocked++
                kotlinx.coroutines.delay(GAP_MS)
            }
            val s = Summary(ok, blocked)
            if (ok + blocked > 0) {
                LogBuffer.i("Mae", "learned: $ok services direct-capable, $blocked need proxy")
            }
            s
        }

    /** One direct probe of a service. Never throws. */
    fun probe(def: MaeServiceDef): ProbeResult {
        val t0 = android.os.SystemClock.elapsedRealtime()
        val ok = runCatching {
            val conn = URL(def.probeUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.useCaches = false
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "SimorghMae/1.0")
                val code = conn.responseCode
                if (code in 200..499) {
                    // drain a little so the answer is really read, not just headers
                    runCatching {
                        conn.inputStream?.use { ins ->
                            val buf = ByteArray(2048)
                            val sink = ByteArrayOutputStream()
                            var total = 0
                            while (total < READ_CAP) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                sink.write(buf, 0, n)
                                total += n
                            }
                        }
                    }
                    true
                } else false
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
        val ms = android.os.SystemClock.elapsedRealtime() - t0
        return ProbeResult(ok, ms)
    }
}
