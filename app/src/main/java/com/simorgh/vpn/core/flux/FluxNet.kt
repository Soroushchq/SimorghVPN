package com.simorgh.vpn.core.flux

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Network identity for the FLUX memory: "wifi" vs "cell" (vs "other"). A route
 * that is dead on one operator says nothing about the next, so memory is keyed
 * per network class — the mlmvpn "net" key without needing location
 * permissions for SSIDs.
 */
object FluxNet {

    fun currentKey(context: Context): String {
        val cm = runCatching {
            context.applicationContext.getSystemService(ConnectivityManager::class.java)
        }.getOrNull() ?: return "unknown"

        // When the VPN is up, the "active" network IS the VPN. Look at every
        // network with real internet instead and prefer the physical transport.
        val candidates = cm.allNetworks.mapNotNull { n ->
            val caps = runCatching { cm.getNetworkCapabilities(n) }.getOrNull()
                ?: return@mapNotNull null
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) caps else null
        }
        if (candidates.isEmpty()) return "unknown"
        return when {
            candidates.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } -> "wifi"
            candidates.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } -> "cell"
            else -> "other"
        }
    }
}
