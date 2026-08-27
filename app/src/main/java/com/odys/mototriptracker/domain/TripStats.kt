package com.odys.mototriptracker.domain

enum class GpsQuality {
    UNKNOWN,
    EXCELLENT,
    GOOD,
    FAIR,
    POOR;

    /** Filled signal bars out of 4 — matches iOS. */
    val barCount: Int
        get() = when (this) {
            EXCELLENT -> 4
            GOOD -> 3
            FAIR -> 2
            POOR -> 1
            UNKNOWN -> 0
        }

    val shortLabel: String
        get() = when (this) {
            UNKNOWN -> "No fix"
            EXCELLENT -> "Excellent"
            GOOD -> "Good"
            FAIR -> "Fair"
            POOR -> "Weak"
        }

    val label: String
        get() = when (this) {
            UNKNOWN -> "GPS —"
            EXCELLENT -> "GPS EXCELLENT"
            GOOD -> "GPS GOOD"
            FAIR -> "GPS FAIR"
            POOR -> "GPS WEAK"
        }

    companion object {
        /** Accuracy buckets match iOS: ≤5 / ≤10 / ≤20 m. */
        fun fromAccuracyMeters(accuracy: Float?): GpsQuality {
            if (accuracy == null || accuracy <= 0f) return UNKNOWN
            return when {
                accuracy <= 5f -> EXCELLENT
                accuracy <= 10f -> GOOD
                accuracy <= 20f -> FAIR
                else -> POOR
            }
        }
    }
}

data class TripStats(
    val speed: Float = 0f,
    val distanceMeters: Float = 0f,
    val tripStartTime: Long = 0L,
    val movingTime: Long = 0L,
    val stoppedTime: Long = 0L,
    val maxSpeed: Float = 0f,
    val currentGForce: Float = 0f,
    val maxGForce: Float = 0f,
    val currentLateralGForce: Float = 0f,
    val maxLateralGForce: Float = 0f,
    val cornerCount: Int = 0,
    val elevation: Float = 0f,
    val avgSpeed: Float = 0f,
    val totalElevationGain: Float = 0f,
    /** Live road speed limit from OSM, null until first lookup succeeds. */
    val roadSpeedLimitKmh: Int? = null,
    val gpsAccuracyMeters: Float? = null,
    val gpsQuality: GpsQuality = GpsQuality.UNKNOWN
) {
    val tripTime: Long
        get() = movingTime + stoppedTime

    val distanceKm: Float
        get() = distanceMeters / 1000f
}
