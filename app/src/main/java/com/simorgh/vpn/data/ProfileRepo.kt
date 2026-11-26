package com.simorgh.vpn.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class Store(
    val profiles: List<Profile> = emptyList(),
    val subscriptions: List<Subscription> = emptyList()
)

class ProfileRepo(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val file: File get() = File(context.applicationContext.filesDir, "profiles.json")
    private val mutex = Mutex()

    val profiles = MutableStateFlow<List<Profile>>(emptyList())
    val subscriptions = MutableStateFlow<List<Subscription>>(emptyList())

    suspend fun load() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val st = readStore()
                profiles.value = st.profiles
                subscriptions.value = st.subscriptions
            }
        }
    }

    private suspend fun persist(transform: (Store) -> Store) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val ns = transform(readStore())
                file.writeText(json.encodeToString(ns))
                profiles.value = ns.profiles
                subscriptions.value = ns.subscriptions
            }
        }
    }

    private fun readStore(): Store =
        runCatching { json.decodeFromString<Store>(file.readText()) }.getOrNull() ?: Store()

    // ------------------------------------------------------------------ api

    suspend fun addFromLinks(text: String): Int {
        val parsed = ShareLinkParser.parseAll(text).map { it.copy(addedAt = System.currentTimeMillis()) }
        if (parsed.isNotEmpty()) persist { it.copy(profiles = it.profiles + parsed) }
        return parsed.size
    }

    suspend fun delete(id: String) = persist { st ->
        st.copy(profiles = st.profiles.filter { it.id != id })
    }

    suspend fun updateLatency(id: String, ms: Long) = persist { st ->
        st.copy(
            profiles = st.profiles.map {
                if (it.id == id) it.copy(lastLatencyMs = ms, lastTestAt = System.currentTimeMillis()) else it
            }
        )
    }

    suspend fun addSubscription(name: String, url: String): Subscription {
        val sub = Subscription(
            id = ShareLinkParser.newId(),
            name = name.ifBlank { "Subscription" },
            url = url.trim(),
            addedAt = System.currentTimeMillis()
        )
        persist { it.copy(subscriptions = it.subscriptions + sub) }
        return sub
    }

    suspend fun removeSubscription(id: String) = persist { st ->
        st.copy(
            subscriptions = st.subscriptions.filter { it.id != id },
            profiles = st.profiles.filter { it.subscriptionId != id }
        )
    }

    suspend fun replaceSubscriptionProfiles(subId: String, list: List<Profile>) = persist { st ->
        st.copy(profiles = st.profiles.filter { it.subscriptionId != subId } + list)
    }
}
