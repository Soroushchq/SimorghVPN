package com.simorgh.vpn.core.config

import kotlinx.serialization.Serializable

/**
 * The signed remote-config channel (msn-guard/shroudly pattern): anti-censorship
 * parameters that must move FASTER than app releases — fragmentation profiles
 * tuned for the current DPI behavior, uTLS fingerprint lists, WARP endpoints,
 * free-config sources — shipped as a small JSON, signed with Ed25519, public
 * key pinned in the APK. Anything that fails verification is ignored; anything
 * absent from the file keeps its built-in value.
 */
@Serializable
data class RemoteConfig(
    val version: Int = 1,
    /** New/patched TLS fragmentation profiles, keyed by name. */
    val fragmentProfiles: List<FragmentProfileEntry> = emptyList(),
    /** Full replacement list for uTLS rotation candidates (validated names). */
    val fingerprints: List<String> = emptyList(),
    /** Extra/alternative WARP endpoints (ip + port). */
    val warpEndpoints: List<WarpEndpointEntry> = emptyList(),
    /** Full replacement list for built-in free-config source URLs (https only). */
    val configSources: List<String> = emptyList(),
    val notes: String = ""
)

@Serializable
data class FragmentProfileEntry(
    val name: String,
    val length: String,
    val interval: String
)

@Serializable
data class WarpEndpointEntry(
    val ip: String,
    val port: Int
)

/**
 * What the verified config changed, live in memory. Consulted by the
 * registries (TlsFragmentProfile / FingerprintRotator / WarpEndpoints /
 * BuiltInSources) so every consumer automatically honors the remote channel.
 */
object RemoteOverrides {

    @Volatile var fragmentProfiles: Map<String, Pair<String, String>> = emptyMap()
    @Volatile var fingerprints: List<String>? = null
    @Volatile var warpIps: List<String>? = null
    @Volatile var warpPorts: List<Int>? = null
    @Volatile var configSources: List<String>? = null
    @Volatile var notes: String = ""
    @Volatile var appliedAt: Long = 0L

    fun apply(cfg: RemoteConfig) {
        fragmentProfiles = cfg.fragmentProfiles
            .filter { it.name.isNotBlank() && it.length.isNotBlank() && it.interval.isNotBlank() }
            .associate { it.name to (it.length to it.interval) }
        fingerprints = cfg.fingerprints.map { it.trim() }.filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
        warpIps = cfg.warpEndpoints.map { it.ip.trim() }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
        warpPorts = cfg.warpEndpoints.map { it.port }.filter { it in 1..65535 }.takeIf { it.isNotEmpty() }
        configSources = cfg.configSources.map { it.trim() }
            .filter { it.startsWith("https://") }.takeIf { it.isNotEmpty() }
        notes = cfg.notes
        appliedAt = System.currentTimeMillis()
    }

    fun clear() {
        fragmentProfiles = emptyMap()
        fingerprints = null
        warpIps = null
        warpPorts = null
        configSources = null
        notes = ""
        appliedAt = 0L
    }
}
