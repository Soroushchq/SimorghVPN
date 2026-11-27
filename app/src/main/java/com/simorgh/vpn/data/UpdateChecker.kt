package com.simorgh.vpn.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Checks GitHub Releases for a newer version (Derafsh CheckUpdate pattern).
 * Compares the tag of the latest release with the running versionName.
 */
object UpdateChecker {

    const val REPO = "Soroushchq/SimorghVPN"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val PAGE = "https://github.com/$REPO/releases/latest"

    data class UpdateInfo(
        val latestTag: String,
        val latestVersion: String,
        val currentVersion: String,
        val hasUpdate: Boolean,
        val releaseUrl: String,
        val apkUrl: String?,
        val releaseName: String?
    )

    suspend fun check(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        val conn = URI(API).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "SimorghVPN/$currentVersion (Android; update-check)")
            val code = conn.responseCode
            if (code == 404) throw IOException("no release published yet")
            if (code !in 200..299) throw IOException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }

            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: "v0.0.0"
            val apk = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]*\\.apk)\"").find(body)?.groupValues?.get(1)
            val name = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)

            val latest = tag.removePrefix("v").substringBefore('-')
            UpdateInfo(
                latestTag = tag,
                latestVersion = latest,
                currentVersion = currentVersion,
                hasUpdate = isNewer(latest, currentVersion),
                releaseUrl = PAGE,
                apkUrl = apk,
                releaseName = name
            )
        } finally {
            conn.disconnect()
        }
    }

    /** a.b.c numeric compare; returns true if [a] > [b]. */
    private fun isNewer(a: String, b: String): Boolean {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
