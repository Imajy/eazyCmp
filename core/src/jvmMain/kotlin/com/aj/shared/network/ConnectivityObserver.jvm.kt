package com.aj.shared.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

actual class ConnectivityObserver actual constructor() {

    private val cachedOnline = AtomicBoolean(true)

    /**
     * Returns the last known connectivity state without blocking the caller.
     * Probing happens on [connectivityFlow] / [refresh] on a background dispatcher.
     */
    actual val isOnline: Boolean
        get() = cachedOnline.get()

    actual val connectivityFlow: Flow<Boolean>
        get() = flow {
            while (true) {
                emit(refresh())
                delay(5_000)
            }
        }.flowOn(Dispatchers.IO).distinctUntilChanged()

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val online = checkConnection()
        cachedOnline.set(online)
        online
    }

    private fun checkConnection(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53), 1500)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
