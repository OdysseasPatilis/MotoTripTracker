package com.odys.mototriptracker.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Shared WGS84-sphere geo helpers (meters + degrees). */
object Geo {
    const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Great-circle distance in meters. */
    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val clamped = min(1.0, sqrt(a))
        return 2 * EARTH_RADIUS_METERS * asin(clamped)
    }

    /** Initial bearing from A→B in degrees [0, 360). */
    fun bearingDegrees(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Double {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLon = Math.toRadians(toLng - fromLng)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    /** Smallest absolute heading difference in degrees [0, 180]. */
    fun headingDeltaDegrees(a: Double, b: Double): Double {
        var delta = kotlin.math.abs(a - b) % 360.0
        if (delta > 180.0) delta = 360.0 - delta
        return delta
    }

    /** Destination point traveling [meters] along [bearingDegrees] from start. */
    fun destination(
        latitude: Double,
        longitude: Double,
        bearingDegrees: Double,
        meters: Double,
    ): Pair<Double, Double> {
        if (meters <= 0) return latitude to longitude
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val angular = meters / EARTH_RADIUS_METERS

        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }
}
