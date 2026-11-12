package com.aj.shared.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import android.os.Looper
import com.aj.shared.api.appContext
import com.aj.shared.api.isAppContextInitialized
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

actual class LocationManager actual constructor() {
    private val locationManager: AndroidLocationManager? by lazy {
        if (!isAppContextInitialized()) return@lazy null
        appContext.getSystemService(Context.LOCATION_SERVICE) as? AndroidLocationManager
    }

    @SuppressLint("MissingPermission")
    actual suspend fun getCurrentLocation(onResult: (LatLng?) -> Unit) {
        val lm = locationManager
        if (lm == null) {
            onResult(null)
            return
        }

        try {
            val providers = lm.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val l = lm.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
            if (bestLocation != null) {
                onResult(LatLng(bestLocation.latitude, bestLocation.longitude))
                return
            }

            val provider = if (lm.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)) {
                AndroidLocationManager.GPS_PROVIDER
            } else if (lm.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)) {
                AndroidLocationManager.NETWORK_PROVIDER
            } else {
                onResult(null)
                return
            }

            val result = withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) {
                                cont.resume(LatLng(location.latitude, location.longitude))
                            }
                            try {
                                lm.removeUpdates(this)
                            } catch (_: Exception) {
                            }
                        }

                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    cont.invokeOnCancellation {
                        try {
                            lm.removeUpdates(listener)
                        } catch (_: Exception) {
                        }
                    }
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
            onResult(result)
        } catch (e: SecurityException) {
            e.printStackTrace()
            onResult(null)
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(null)
        }
    }

    @SuppressLint("MissingPermission")
    actual fun observeLocation(intervalMillis: Long): Flow<LatLng?> = callbackFlow {
        val lm = locationManager
        if (lm == null || !LocationPolicy.requireForegroundForContinuousUpdates()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(LatLng(location.latitude, location.longitude))
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            val provider = if (lm.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)) {
                AndroidLocationManager.GPS_PROVIDER
            } else {
                AndroidLocationManager.NETWORK_PROVIDER
            }

            lm.requestLocationUpdates(
                provider,
                intervalMillis,
                0f,
                listener,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
            trySend(null)
        } catch (e: Exception) {
            e.printStackTrace()
            trySend(null)
        }

        awaitClose {
            lm.removeUpdates(listener)
        }
    }.distinctUntilChanged()
}
