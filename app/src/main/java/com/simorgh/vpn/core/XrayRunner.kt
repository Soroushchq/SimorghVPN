package com.simorgh.vpn.core

import com.simorgh.vpn.util.LogBuffer
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Thin wrapper around the go-mobile CoreController API of libv2ray.aar.
 */
class XrayRunner {

    var controller: CoreController? = null
        private set

    val isRunning: Boolean
        get() = runCatching { controller?.isRunning ?: false }.getOrDefault(false)

    fun start(configJson: String, tunFd: Int): Boolean {
        val cb = object : CoreCallbackHandler {
            override fun startup(): Long {
                LogBuffer.i("Xray", "core loop started")
                return 0
            }

            override fun shutdown(): Long {
                LogBuffer.i("Xray", "core loop stopped")
                return 0
            }

            override fun onEmitStatus(p0: Long, p1: String?): Long = 0
        }
        val c = Libv2ray.newCoreController(cb)
        controller = c
        c.startLoop(configJson, tunFd)
        val ok = runCatching { c.isRunning }.getOrDefault(false)
        if (!ok) LogBuffer.e("Xray", "core did not enter running state")
        return ok
    }

    /** Asks the core to stop (async on the Go side). */
    fun stop() {
        runCatching { controller?.stopLoop() }
            .onFailure { LogBuffer.w("Xray", "stopLoop: ${it.message}") }
    }

    /** Drops the controller reference after [isRunning] has settled to false. */
    fun release() {
        controller = null
    }

    fun statsRaw(): String? =
        runCatching { controller?.queryAllOutboundTrafficStats() }.getOrNull()

    companion object {
        const val TEST_URL = "https://www.gstatic.com/generate_204"

        /** Raw stats format: "tag,dir,val;tag,dir,val;..." → proxy up/down totals (bytes). */
        fun parseStats(raw: String?): Pair<Long, Long> {
            if (raw.isNullOrBlank()) return 0L to 0L
            var up = 0L
            var down = 0L
            raw.split(';').forEach { entry ->
                val parts = entry.split(',')
                if (parts.size == 3) {
                    val v = parts[2].trim().toLongOrNull() ?: 0L
                    when {
                        parts[0] == "proxy" && parts[1] == "uplink" -> up = v
                        parts[0] == "proxy" && parts[1] == "downlink" -> down = v
                    }
                }
            }
            return up to down
        }

        fun measureDelay(configJson: String, url: String = TEST_URL): Long =
            runCatching { Libv2ray.measureOutboundDelay(configJson, url) }.getOrDefault(-1L)
    }
}
