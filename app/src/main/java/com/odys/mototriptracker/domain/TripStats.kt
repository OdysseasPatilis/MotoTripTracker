package com.odys.mototriptracker.domain

data class TripStats(
    val speed: Float = 0f,
    val distanceMeters: Float = 0f,
    val tripStartTime: Long = 0L,
    val movingTime: Long = 0L,
    val stoppedTime: Long = 0L,
    val maxSpeed: Float = 0f
) {
    val tripTime: Long
        get() = movingTime + stoppedTime

    val distanceKm: Float
        get() = distanceMeters / 1000f
}