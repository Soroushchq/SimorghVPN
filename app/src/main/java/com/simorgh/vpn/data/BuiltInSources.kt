package com.simorgh.vpn.data

import com.simorgh.vpn.core.config.RemoteOverrides

/**
 * Built-in free-config sources (Derafsh pattern).
 *
 * A pool of public, frequently-refreshed, Iran-friendly config feeds. They are
 * presented to the user as neutral numbered entries ("سیمرغ ۱", "سیمرغ ۲", …) —
 * never by URL. Exactly ONE entry (the active source) is materialized as the
 * built-in subscription ([BUILTIN_SUB_ID]) and auto-refreshed; switching source
 * rewrites that subscription's URL and triggers an immediate refresh.
 *
 * Every URL was validated live (HTTP 200 + ≥100 parseable configs) before
 * shipping. If the network is unavailable on first launch, [SEED_ASSET] ships
 * a snapshot inside the APK so the app is never empty.
 *
 * v0.4: the list consults the signed remote-config channel — a source that
 * dies can be replaced without a release.
 */
object BuiltInSources {

    /** Stable id of the single built-in subscription. */
    const val BUILTIN_SUB_ID = "__simorgh_builtin__"

    /** Asset bundled in the APK with a health-checked snapshot of the default pool. */
    const val SEED_ASSET = "builtin_seed.txt"

    /** Refresh the built-in subscription at most every 4 hours. */
    const val REFRESH_INTERVAL_MS = 4L * 60 * 60 * 1000

    /**
     * Validated free-config feeds (default pool; index 0 mirrors [SEED_ASSET]).
     */
    val DEFAULT_SOURCE_URLS = listOf(
        // 0xRadikal "top100" — health-checked top 100
        "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/top100.txt",
        "https://raw.githubusercontent.com/MhdiTaheri/V2rayCollector_Py/HEAD/sub/Mix/mix.txt",
        "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/HEAD/server.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/HEAD/sub/sub_merge.txt",
        "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/HEAD/subscriptions/v2ray/all_sub.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Sub1.txt",
        "https://raw.githubusercontent.com/itsyebekhe/PSG/HEAD/subscriptions/xray/mix"
    )

    /** Live source list: remote override → built-ins. */
    fun sourceUrls(): List<String> =
        RemoteOverrides.configSources?.takeIf { it.isNotEmpty() } ?: DEFAULT_SOURCE_URLS

    fun count(): Int = sourceUrls().size

    fun sourceUrl(index: Int): String =
        sourceUrls()[index.coerceIn(0, sourceUrls().lastIndex)]
}
