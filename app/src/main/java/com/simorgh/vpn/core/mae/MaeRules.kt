package com.simorgh.vpn.core.mae

/**
 * MAE → Xray routing rules translation.
 *
 * For every service with a decided route, one field rule is generated:
 *   DIRECT services → outboundTag "direct"  (faster; saves the tunnel)
 *   PROXY services  → outboundTag "proxy"   (explicit pin; meaningful in
 *                     GLOBAL mode where the default is already proxy)
 *
 * Rules are inserted by XrayConfigBuilder BEFORE the geo rules — a per-service
 * decision must beat the generic Iran-bypass list (mlmvpn RouteProvider order).
 */
object MaeRules {

    data class ServiceRoute(val outboundTag: String, val domains: List<String>)

    /** Build routing rules from the current MAE state; empty when disabled/unknown. */
    fun build(state: MaeState): List<ServiceRoute> {
        val out = mutableListOf<ServiceRoute>()
        for (def in MaeRegistry.SERVICES) {
            val st = state.services[def.id] ?: continue
            when (st.effective) {
                MaeRoute.DIRECT -> out += ServiceRoute("direct", def.domains)
                MaeRoute.PROXY -> out += ServiceRoute("proxy", def.domains)
                MaeRoute.AUTO -> Unit // no evidence yet — leave to the default path
            }
        }
        return out
    }
}
