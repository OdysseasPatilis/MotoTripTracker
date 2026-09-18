package com.odys.mototriptracker.domain

/**
 * Session accelerometer / G readings. Android sensor wiring lives in the data layer.
 */
interface GForceTracker {
    val currentGForce: Float
    val maxSessionGForce: Float
    val currentLateralGForce: Float
    val maxSessionLateralGForce: Float

    fun startTracking(resetSession: Boolean = true)
    fun stopTracking()
}
