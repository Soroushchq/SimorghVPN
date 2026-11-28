package com.simorgh.vpn.core.anticensor

import com.simorgh.vpn.core.config.RemoteOverrides
import kotlinx.serialization.Serializable

/**
 * TLS ClientHello fragmentation presets, Iran-DPI-tuned (msn-guard pattern:
 * split the ClientHello into small random chunks with small random delays so
 * the DPI never sees the SNI/fingerprint in one packet).
 *
 * Maps 1:1 onto Xray-core `streamSettings.sockopt.fragment`:
 *   { "packets": "tlshello", "length": "a-b", "interval": "c-d" }
 *
 * v0.4: the built-in enum stays the cold-start default; the signed
 * remote-config channel can patch these specs and add new named profiles at
 * runtime (see [specOf] / [profileNames]).
 */
@Serializable
enum class TlsFragmentProfile(val lengthSpec: String, val intervalSpec: String) {
    /** Soft: 40–100 byte chunks every 10–20 ms — near-zero overhead, beats most SNI sniffers. */
    IRAN_LIGHT("40-100", "10-20"),

    /** Medium: 10–50 byte chunks every 5–15 ms — heavier confusion, still fine on 3G/4G. */
    IRAN_MEDIUM("10-50", "5-15"),

    /** Aggressive: 1–10 byte chunks every 5–10 ms — msn-guard style micro-chunks for hostile networks. */
    IRAN_AGGRESSIVE("1-10", "5-10");

    companion object {
        fun of(raw: String?): TlsFragmentProfile =
            entries.firstOrNull { it.name == raw } ?: IRAN_LIGHT
    }
}

/** A concrete fragmentation spec — either a built-in or a remote-config profile. */
data class FragmentSpec(
    val name: String,
    val lengthSpec: String,
    val intervalSpec: String
)

object TlsFragmentProfiles {

    /** Resolve a profile name to its live spec: remote override first, built-ins second. */
    fun specOf(raw: String?): FragmentSpec {
        val name = raw?.trim().orEmpty()
        RemoteOverrides.fragmentProfiles[name]?.let { (len, int) ->
            return FragmentSpec(name, len, int)
        }
        val builtIn = TlsFragmentProfile.of(name)
        return FragmentSpec(builtIn.name, builtIn.lengthSpec, builtIn.intervalSpec)
    }

    /** Every selectable profile name: built-ins + remote-only additions. */
    fun profileNames(): List<String> {
        val remote = RemoteOverrides.fragmentProfiles.keys.filter { it !in builtInNames }
        return builtInNames + remote
    }

    /** One-line human summary of a profile for the settings UI. */
    fun describe(name: String): String {
        val s = specOf(name)
        return "${s.lengthSpec} / ${s.intervalSpec} ms"
    }

    private val builtInNames = TlsFragmentProfile.entries.map { it.name }
}
