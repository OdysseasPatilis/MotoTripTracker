package com.odys.mototriptracker.domain

/**
 * Converts consecutive GPS fixes into a distance increment that won't blow up average speed.
 * Mirrors iOS `RideDistanceFilter`.
 */
object RideDistanceFilter {
    private const val ABSOLUTE_MAX_METERS = 2_000.0
    private const val FALLBACK_MAX_AVG_KMH = 300.0

    /**
     * Prefer GPS speed × elapsed time as a ceiling. Geographic distance is only a sanity check
     * (jitter of 50–80 m/s would otherwise imply 200–300 km/h of fake distance).
     */
    fun distanceDelta(
        geographicMeters: Double,
        speedMps: Double,
        timeDeltaSeconds: Double
    ): Double {
        if (geographicMeters <= 0.0 || speedMps <= 0.0 || timeDeltaSeconds <= 0.0) return 0.0

        val fromSpeed = speedMps * timeDeltaSeconds
        val allowed = minOf(ABSOLUTE_MAX_METERS, fromSpeed * 1.2 + 8.0)

        if (geographicMeters > allowed * 2.5 + 40.0) {
            return 0.0
        }
        return minOf(geographicMeters, allowed)
    }

    /** Average cannot exceed peak; also drops impossible values from bad distance. */
    fun averageSpeedKmh(
        distanceMeters: Double,
        movingTimeSeconds: Long,
        maxSpeedKmh: Double
    ): Float {
        if (movingTimeSeconds <= 0L || distanceMeters <= 0.0) return 0f
        val hours = movingTimeSeconds / 3600.0
        val raw = (distanceMeters / 1000.0) / hours
        val cap = if (maxSpeedKmh > 0.0) maxSpeedKmh else FALLBACK_MAX_AVG_KMH
        return minOf(raw, cap).toFloat()
    }
}
