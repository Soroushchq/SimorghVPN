package com.simorgh.vpn.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.settingsStore by preferencesDataStore(name = "simorgh_settings")

@Serializable
enum class RoutingMode { IR_BYPASS, GLOBAL }

@Serializable
enum class TunnelMode { HEV, XRAY_TUN, PROXY_ONLY }

/** Per-app proxy (split tunneling) mode — Derafsh/V2rayNG perAppProxy pattern. */
@Serializable
enum class PerAppMode { OFF, ALLOW, DENY }

@Serializable
data class SimorghSettings(
    val routingMode: RoutingMode = RoutingMode.IR_BYPASS,
    val tunnelMode: TunnelMode = TunnelMode.HEV,
    val ipv6: Boolean = false,
    val allowInsecure: Boolean = false,
    val socksPort: Int = 10808,
    val mtu: Int = 1500,
    val remoteDns: String = "1.1.1.1",
    val domesticDns: String = "1.1.1.1",
    val activeProfileId: String = "",
    /** Active index in [BuiltInSources.SOURCE_URLS]. */
    val builtinSourceIndex: Int = 0,
    /** Kill-switch: on unexpected engine death, hold the TUN black-hole instead of leaking. */
    val killSwitch: Boolean = true,
    /** Auto-connect when the app opens. */
    val autoConnect: Boolean = false,
    /** Auto-connect after device boot / app update (needs the BootReceiver toggle). */
    val autoStartBoot: Boolean = false,
    val perAppMode: PerAppMode = PerAppMode.OFF,
    val perAppPackages: Set<String> = emptySet()
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
        val BUILTIN_SOURCE = intPreferencesKey("builtin_source_index")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val AUTO_START_BOOT = booleanPreferencesKey("auto_start_boot")
        val PER_APP_MODE = stringPreferencesKey("per_app_mode")
        val PER_APP_PACKAGES = stringSetPreferencesKey("per_app_packages")
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
                activeProfileId = p[Keys.ACTIVE] ?: "",
                builtinSourceIndex = (p[Keys.BUILTIN_SOURCE] ?: 0).coerceIn(0, BuiltInSources.count() - 1),
                killSwitch = p[Keys.KILL_SWITCH] ?: true,
                autoConnect = p[Keys.AUTO_CONNECT] ?: false,
                autoStartBoot = p[Keys.AUTO_START_BOOT] ?: false,
                perAppMode = enumOf(p[Keys.PER_APP_MODE], PerAppMode.OFF),
                perAppPackages = p[Keys.PER_APP_PACKAGES] ?: emptySet()
            )
        }
    }

    /** One-shot synchronous snapshot (BootReceiver, TileService). */
    suspend fun snapshot(context: Context): SimorghSettings = flow(context).first()

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
    suspend fun setBuiltinSourceIndex(context: Context, index: Int) =
        edit(context) { it[Keys.BUILTIN_SOURCE] = index.coerceIn(0, BuiltInSources.count() - 1) }
    suspend fun setKillSwitch(context: Context, v: Boolean) = edit(context) { it[Keys.KILL_SWITCH] = v }
    suspend fun setAutoConnect(context: Context, v: Boolean) = edit(context) { it[Keys.AUTO_CONNECT] = v }
    suspend fun setAutoStartBoot(context: Context, v: Boolean) = edit(context) { it[Keys.AUTO_START_BOOT] = v }
    suspend fun setPerAppMode(context: Context, v: PerAppMode) = edit(context) { it[Keys.PER_APP_MODE] = v.name }
    suspend fun setPerAppPackages(context: Context, pkgs: Set<String>) =
        edit(context) { it[Keys.PER_APP_PACKAGES] = pkgs }
}
