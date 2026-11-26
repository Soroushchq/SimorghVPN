package com.simorgh.vpn.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "simorgh_settings")

enum class RoutingMode { IR_BYPASS, GLOBAL }

enum class TunnelMode { HEV, XRAY_TUN, PROXY_ONLY }

data class SimorghSettings(
    val routingMode: RoutingMode = RoutingMode.IR_BYPASS,
    val tunnelMode: TunnelMode = TunnelMode.HEV,
    val ipv6: Boolean = false,
    val allowInsecure: Boolean = false,
    val socksPort: Int = 10808,
    val mtu: Int = 1500,
    val remoteDns: String = "1.1.1.1",
    val domesticDns: String = "1.1.1.1",
    val activeProfileId: String = ""
)

object SettingsStore {

    private object Keys {
        val ROUTING = stringPreferencesKey("routing_mode")
        val TUNNEL = stringPreferencesKey("tunnel_mode")
        val IPV6 = booleanPreferencesKey("ipv6")
        val INSECURE = booleanPreferencesKey("allow_insecure")
        val SOCKS_PORT = intPreferencesKey("socks_port")
        val MTU = intPreferencesKey("mtu")
        val REMOTE_DNS = stringPreferencesKey("remote_dns")
        val DOMESTIC_DNS = stringPreferencesKey("domestic_dns")
        val ACTIVE = stringPreferencesKey("active_profile_id")
    }

    fun flow(context: Context): Flow<SimorghSettings> {
        val ds = context.applicationContext.settingsStore
        return ds.data.map { p ->
            SimorghSettings(
                routingMode = enumOf(p[Keys.ROUTING], RoutingMode.IR_BYPASS),
                tunnelMode = enumOf(p[Keys.TUNNEL], TunnelMode.HEV),
                ipv6 = p[Keys.IPV6] ?: false,
                allowInsecure = p[Keys.INSECURE] ?: false,
                socksPort = (p[Keys.SOCKS_PORT] ?: 10808).coerceIn(1024, 65535),
                mtu = (p[Keys.MTU] ?: 1500).coerceIn(576, 65535),
                remoteDns = p[Keys.REMOTE_DNS]?.trim().takeUnless { it.isNullOrBlank() } ?: "1.1.1.1",
                domesticDns = p[Keys.DOMESTIC_DNS]?.trim().takeUnless { it.isNullOrBlank() } ?: "1.1.1.1",
                activeProfileId = p[Keys.ACTIVE] ?: ""
            )
        }
    }

    private inline fun <reified T : Enum<T>> enumOf(raw: String?, def: T): T {
        if (raw.isNullOrBlank()) return def
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(def)
    }

    private suspend fun edit(context: Context, block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.applicationContext.settingsStore.edit(block)
    }

    suspend fun setRoutingMode(context: Context, v: RoutingMode) = edit(context) { it[Keys.ROUTING] = v.name }
    suspend fun setTunnelMode(context: Context, v: TunnelMode) = edit(context) { it[Keys.TUNNEL] = v.name }
    suspend fun setIpv6(context: Context, v: Boolean) = edit(context) { it[Keys.IPV6] = v }
    suspend fun setAllowInsecure(context: Context, v: Boolean) = edit(context) { it[Keys.INSECURE] = v }
    suspend fun setSocksPort(context: Context, v: Int) = edit(context) { it[Keys.SOCKS_PORT] = v }
    suspend fun setMtu(context: Context, v: Int) = edit(context) { it[Keys.MTU] = v }
    suspend fun setRemoteDns(context: Context, v: String) = edit(context) { it[Keys.REMOTE_DNS] = v }
    suspend fun setDomesticDns(context: Context, v: String) = edit(context) { it[Keys.DOMESTIC_DNS] = v }
    suspend fun setActiveProfile(context: Context, id: String) = edit(context) { it[Keys.ACTIVE] = id }
}
