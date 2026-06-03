package com.aj.shared.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLLocation
import platform.darwin.NSObject
import platform.Foundation.NSError

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.channels.awaitClose

actual class LocationManager actual constructor() {
    
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun getCurrentLocation(onResult: (LatLng?) -> Unit) {
        val manager = CLLocationManager()
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val location = didUpdateLocations.firstOrNull() as? CLLocation
                if (location != null) {
                    val lat = location.coordinate.useContents { latitude }
                    val lng = location.coordinate.useContents { longitude }
                    onResult(LatLng(lat, lng))
                } else {
                    onResult(null)
                }
                manager.stopUpdatingLocation()
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                onResult(null)
                manager.stopUpdatingLocation()
            }
        }
        manager.delegate = delegate
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun observeLocation(intervalMillis: Long): Flow<LatLng?> = callbackFlow {
        val manager = CLLocationManager()
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val location = didUpdateLocations.firstOrNull() as? CLLocation
                if (location != null) {
                    val lat = location.coordinate.useContents { latitude }
                    val lng = location.coordinate.useContents { longitude }
                    trySend(LatLng(lat, lng))
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                trySend(null)
            }
        }
        manager.delegate = delegate
        manager.distanceFilter = 0.0
        manager.desiredAccuracy = platform.CoreLocation.kCLLocationAccuracyBest
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()

        awaitClose {
            manager.stopUpdatingLocation()
        }
    }.distinctUntilChanged()
}
