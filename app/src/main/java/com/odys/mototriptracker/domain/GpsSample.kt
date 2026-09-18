package com.odys.mototriptracker.domain

/**
 * Platform-agnostic GPS fix used by trip tracking domain logic.
 * Mapped from Android [android.location.Location] at the service boundary.
 */
data class GpsSample(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speedMps: Float = 0f,
    val accuracyMeters: Float? = null,
    val bearingDeg: Float? = null,
    val timeMs: Long = 0L,
    val hasAltitude: Boolean = false,
    val hasSpeed: Boolean = false,
)
