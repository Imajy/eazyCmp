package com.aj.shared.location

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calculates the great-circle distance between two LatLng points using the Haversine formula.
 * @return distance in meters
 */
fun LatLng.distanceTo(other: LatLng): Double {
    val earthRadiusMeters = 6371000.0

    val dLat = (other.latitude - this.latitude) * (PI / 180.0)
    val dLon = (other.longitude - this.longitude) * (PI / 180.0)

    val lat1Rad = this.latitude * (PI / 180.0)
    val lat2Rad = other.latitude * (PI / 180.0)

    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1Rad) * cos(lat2Rad) *
            sin(dLon / 2) * sin(dLon / 2)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

/**
 * Calculates distance between two LatLng points in kilometers.
 */
fun LatLng.distanceToKm(other: LatLng): Double {
    return distanceTo(other) / 1000.0
}

/**
 * Calculates the initial compass bearing (heading in degrees from 0 to 360) from this point to another.
 */
fun LatLng.bearingTo(other: LatLng): Double {
    val lat1 = this.latitude * (PI / 180.0)
    val lat2 = other.latitude * (PI / 180.0)
    val dLon = (other.longitude - this.longitude) * (PI / 180.0)

    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val bearingRad = atan2(y, x)

    return (bearingRad * (180.0 / PI) + 360.0) % 360.0
}

/**
 * Checks if another LatLng point is within a given radius in meters from this point.
 */
fun LatLng.isWithinRadius(center: LatLng, radiusMeters: Double): Boolean {
    return this.distanceTo(center) <= radiusMeters
}

/**
 * Formats distance between two points into human-readable format ("450 m" or "3.2 km").
 */
fun LatLng.formatDistance(other: LatLng): String {
    val meters = distanceTo(other)
    return if (meters < 1000) {
        "${meters.toInt()} m"
    } else {
        val km = (meters / 100.0).toInt() / 10.0
        "$km km"
    }
}

/**
 * 2D Kalman Filter for real-time GPS location smoothing.
 * Filters out sensor jitter and jumps during live tracking (e.g. driver/courier navigation).
 */
class GpsLocationSmoother(
    private val processNoise: Double = 3.0,
    private val measurementNoise: Double = 10.0
) {
    private var latEstimate = 0.0
    private var lngEstimate = 0.0
    private var latVariance = 1.0
    private var lngVariance = 1.0
    private var isInitialized = false

    /**
     * Feeds a raw GPS reading and returns the filtered, smoothed LatLng.
     */
    fun process(raw: LatLng): LatLng {
        if (!isInitialized) {
            latEstimate = raw.latitude
            lngEstimate = raw.longitude
            latVariance = measurementNoise
            lngVariance = measurementNoise
            isInitialized = true
            return raw
        }

        // Time update (predict)
        latVariance += processNoise
        lngVariance += processNoise

        // Measurement update (correct latitude)
        val latGain = latVariance / (latVariance + measurementNoise)
        latEstimate += latGain * (raw.latitude - latEstimate)
        latVariance *= (1.0 - latGain)

        // Measurement update (correct longitude)
        val lngGain = lngVariance / (lngVariance + measurementNoise)
        lngEstimate += lngGain * (raw.longitude - lngEstimate)
        lngVariance *= (1.0 - lngGain)

        return LatLng(latEstimate, lngEstimate)
    }

    fun reset() {
        isInitialized = false
    }
}
