package com.aj.shared.location

import kotlinx.coroutines.flow.Flow

data class LatLng(
    val latitude: Double,
    val longitude: Double
)

expect class LocationManager() {
    suspend fun getCurrentLocation(onResult: (LatLng?) -> Unit)
    fun observeLocation(intervalMillis: Long = 5000): Flow<LatLng?>
}
