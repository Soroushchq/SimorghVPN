package com.simorgh.vpn.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.simorgh.vpn.ServiceLocator

/**
 * JSON backup/restore (Derafsh BackupActivity pattern, via SAF).
 *
 * Backup = user profiles + subscriptions (excluding the built-in free-config
 * subscription, which is self-managing) + settings. Import merges: duplicates
 * (same protocol/host/port/secret/path) are skipped, id collisions get fresh ids.
 */
object BackupManager {

    @Serializable
    data class BackupData(
        val version: Int = 1,
        val exportedAt: Long,
        val profiles: List<Profile> = emptyList(),
        val subscriptions: List<Subscription> = emptyList(),
        val settings: SimorghSettings? = null
    )

    data class RestoreResult(val profilesAdded: Int, val subscriptionsAdded: Int, val settingsApplied: Boolean)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun export(): String {
        val st = ServiceLocator.profileRepo
        val data = BackupData(
            exportedAt = System.currentTimeMillis(),
            profiles = st.profiles.value.filter { it.subscriptionId != BuiltInSources.BUILTIN_SUB_ID },
            subscriptions = st.subscriptions.value.filter { it.id != BuiltInSources.BUILTIN_SUB_ID },
            settings = ServiceLocator.settings.value
        )
        return json.encodeToString(data)
    }

    suspend fun restore(context: Context, text: String): RestoreResult {
        val data = runCatching { json.decodeFromString<BackupData>(text) }
            .getOrElse { throw IllegalArgumentException("not a Simorgh backup") }
        val (p, s) = ServiceLocator.profileRepo.importBackup(data.profiles, data.subscriptions)
        data.settings?.let { st -> applySettings(context, st) }
        return RestoreResult(p, s, data.settings != null)
    }

    private suspend fun applySettings(context: Context, st: SimorghSettings) {
        SettingsStore.setRoutingMode(context, st.routingMode)
        SettingsStore.setTunnelMode(context, st.tunnelMode)
        SettingsStore.setIpv6(context, st.ipv6)
        SettingsStore.setAllowInsecure(context, st.allowInsecure)
        SettingsStore.setSocksPort(context, st.socksPort)
        SettingsStore.setMtu(context, st.mtu)
        SettingsStore.setRemoteDns(context, st.remoteDns)
        SettingsStore.setDomesticDns(context, st.domesticDns)
        if (st.activeProfileId.isNotBlank()) SettingsStore.setActiveProfile(context, st.activeProfileId)
        SettingsStore.setKillSwitch(context, st.killSwitch)
        SettingsStore.setAutoConnect(context, st.autoConnect)
        SettingsStore.setAutoStartBoot(context, st.autoStartBoot)
        SettingsStore.setPerAppMode(context, st.perAppMode)
        SettingsStore.setPerAppPackages(context, st.perAppPackages)
        if (st.engineId.isNotBlank()) SettingsStore.setEngineId(context, st.engineId)
        // v0.3 anti-censor (fingerprintOverride is transient — never restored)
        SettingsStore.setTlsFragment(context, st.tlsFragment)
        SettingsStore.setTlsFragmentProfile(context, st.tlsFragmentProfile)
        SettingsStore.setUtlsRotation(context, st.utlsRotation)
        SettingsStore.setFallbackChain(context, st.fallbackChain)
        // v0.3.1 WARP (endpoint overrides are transient — never restored;
        // identities live in the Keystore-sealed vault, never in backups)
        SettingsStore.setWarpPort(context, st.warpPort)
        SettingsStore.setWarpPortRotation(context, st.warpPortRotation)
    }
}
