package com.simorgh.vpn.core.engine

/**
 * Engine registry (mlmvpn multi-engine chassis pattern): engines register a
 * factory at app start (both processes) and the VPN service instantiates the
 * one named by `settings.engineId`. Adding a v0.3 engine is a one-line
 * registration — no service changes.
 */
object EngineRegistry {

    const val XRAY = "xray"
    const val WARP = "warp"

    data class EngineInfo(val id: String, val label: String, val create: () -> VpnEngine)

    private val engines = LinkedHashMap<String, EngineInfo>()

    fun register(id: String, label: String, create: () -> VpnEngine) {
        engines[id] = EngineInfo(id, label, create)
    }

    /** Idempotent default set; called from SimorghApp in every process. */
    fun registerDefaults() {
        if (engines.isNotEmpty()) return
        register(XRAY, "Xray (VLESS/REALITY · VMess · Trojan · Shadowsocks)") { XrayEngine() }
        register(WARP, "WARP (رایگان کلادفلر · بدون سرور)") { WarpEngine() }
    }

    fun ids(): List<String> = engines.keys.toList()

    fun info(id: String): EngineInfo? = engines[id]

    fun create(id: String): VpnEngine? = engines[id]?.create()

    /** Fallback id when settings carry a blank/unknown engine. */
    fun defaultId(): String =
        if (engines.containsKey(XRAY)) XRAY else engines.keys.firstOrNull() ?: XRAY
}
