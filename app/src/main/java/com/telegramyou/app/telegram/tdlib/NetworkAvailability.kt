package com.telegramyou.app.telegram.tdlib

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Android knows when no default network exists even before TDLib times out. */
internal fun networkUnavailable(context: Context) = callbackFlow {
    val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    if (connectivity == null) {
        trySend(false)
        close()
        return@callbackFlow
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(false) }
        override fun onLost(network: Network) {
            val active = connectivity.activeNetwork
            trySend(active == null || active == network)
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            // VALIDATED depends on Android's probe servers; a working proxy
            // must not be labelled offline just because those are blocked.
            trySend(!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        }
    }
    connectivity.registerDefaultNetworkCallback(callback)
    val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
    trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true)
    awaitClose { connectivity.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
