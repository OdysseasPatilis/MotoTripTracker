package com.odys.mototriptracker.domain

/** Latest Google Roads snap for a raw GPS fix. */
data class SnappedRoad(
    val latitude: Double,
    val longitude: Double,
    val placeId: String?,
    val rawLatitude: Double,
    val rawLongitude: Double,
    val snappedAtMs: Long,
)
