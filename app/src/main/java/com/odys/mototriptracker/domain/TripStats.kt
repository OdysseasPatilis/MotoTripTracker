package com.odys.mototriptracker.domain

data class TripStats(
    val speed: Float = 0f,
    val distanceMeters: Float = 0f,
    val tripStartTime: Long = 0L,
    val movingTime: Long = 0L,
    val stoppedTime: Long = 0L,
    val maxSpeed: Float = 0f,
    val currentGForce: Float = 0f,
    val maxGForce: Float = 0f,
    val elevation: Float = 0f,
    val avgSpeed: Float = 0f,
    val totalElevationGain: Float = 0f,
    /** Live road speed limit from OSM, null until first lookup succeeds. */
    val roadSpeedLimitKmh: Int? = null
) {
    val tripTime: Long
        get() = movingTime + stoppedTime

    val distanceKm: Float
        get() = distanceMeters / 1000f
}