package com.simorgh.vpn.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class SubscriptionRepo(private val context: Context, private val repo: ProfileRepo) {

    suspend fun add(name: String, url: String): Subscription =
        repo.addSubscription(name, url)

    suspend fun refresh(sub: Subscription): Int = withContext(Dispatchers.IO) {
        val body = fetch(sub.url)
        val list = ShareLinkParser.parseSubscriptionBody(body).map {
            it.copy(
                id = ShareLinkParser.newId(),
                subscriptionId = sub.id,
                addedAt = System.currentTimeMillis()
            )
        }
        if (list.isNotEmpty()) {
            repo.replaceSubscriptionProfiles(sub.id, list)
            repo.touchSubscription(sub.id)
        }
        list.size
    }

    /**
     * Refresh the built-in free-config subscription ([BuiltInSources.BUILTIN_SUB_ID])
     * from the given source URL. Stamps [Subscription.lastUpdateAt] on success.
     */
    suspend fun refreshBuiltin(sourceUrl: String): Int = withContext(Dispatchers.IO) {
        val sub = repo.ensureSubscription(
            BuiltInSources.BUILTIN_SUB_ID,
            "", // name is localised by the UI; stored name kept in sync there
            sourceUrl
        )
        refresh(sub)
    }

    /**
     * First-launch offline seed: import the snapshot bundled in the APK so the
     * app has servers even without network access. Only runs when the built-in
     * subscription has no profiles yet.
     */
    suspend fun seedBuiltinFromAssetIfEmpty(sourceUrl: String): Int = withContext(Dispatchers.IO) {
        val sub = repo.ensureSubscription(BuiltInSources.BUILTIN_SUB_ID, "", sourceUrl)
        val hasProfiles = repo.profiles.value.any { it.subscriptionId == sub.id }
        if (hasProfiles) return@withContext 0
        val body = runCatching {
            context.applicationContext.assets.open(BuiltInSources.SEED_ASSET)
                .bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val list = ShareLinkParser.parseSubscriptionBody(body).map {
            it.copy(
                id = ShareLinkParser.newId(),
                subscriptionId = sub.id,
                addedAt = System.currentTimeMillis()
            )
        }
        if (list.isNotEmpty()) {
            repo.replaceSubscriptionProfiles(sub.id, list)
            repo.touchSubscription(sub.id)
        }
        list.size
    }

    private fun fetch(url: String): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "SimorghVPN/0.1 (Android; subscription)")
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
