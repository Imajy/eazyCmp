package com.aj.shared.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.aj.shared.api.appContext
import com.aj.shared.api.isAppContextInitialized
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

actual class ConnectivityObserver actual constructor() {

    private val connectivityManager: ConnectivityManager? by lazy {
        if (!isAppContextInitialized()) return@lazy null
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    actual val isOnline: Boolean
        get() {
            val cm = connectivityManager ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

    actual val connectivityFlow: Flow<Boolean>
        get() {
            val cm = connectivityManager ?: return flowOf(true)
            return callbackFlow {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(true)
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }
                }

                trySend(isOnline)

                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, callback)

                awaitClose {
                    cm.unregisterNetworkCallback(callback)
                }
            }.distinctUntilChanged()
        }
}
