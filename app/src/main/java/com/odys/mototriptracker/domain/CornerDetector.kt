package com.odys.mototriptracker.domain

import android.location.Location
import kotlin.math.abs

/**
 * Counts corners from GPS bearing changes while moving, and estimates peak lateral G
 * via [v² / r] for the turn radius implied by the heading change.
 */
class CornerDetector {
    private var lastBearing: Float? = null
    private var lastLocation: Location? = null
    private var accumulatedTurnDeg = 0f
    private var inCorner = false

    var cornerCount: Int = 0
        private set
    var maxEstimatedLateralG: Float = 0f
        private set

    fun reset() {
        lastBearing = null
        lastLocation = null
        accumulatedTurnDeg = 0f
        inCorner = false
        cornerCount = 0
        maxEstimatedLateralG = 0f
    }

    fun onLocation(location: Location, speedMps: Float): Boolean {
        if (!location.hasBearing() || speedMps < MIN_SPEED_MPS) {
            finishCornerIfNeeded()
            lastBearing = null
            lastLocation = location
            return false
        }

        val bearing = location.bearing
        val prevBearing = lastBearing
        val prev = lastLocation
        lastBearing = bearing
        lastLocation = location

        if (prevBearing == null || prev == null) return false

        val delta = shortestAngleDeg(bearing - prevBearing)
        val absDelta = abs(delta)
        if (absDelta < NOISE_DEG) return false

        // Same turn direction → accumulate; reverse → finish previous corner.
        val sameDirection = accumulatedTurnDeg == 0f ||
            (accumulatedTurnDeg > 0f && delta > 0f) ||
            (accumulatedTurnDeg < 0f && delta < 0f)

        if (!sameDirection) {
            finishCornerIfNeeded()
        }

        accumulatedTurnDeg += delta
        inCorner = abs(accumulatedTurnDeg) >= CORNER_START_DEG

        val distance = prev.distanceTo(location)
        if (distance > 1f && absDelta > 0.5f) {
            val turnRad = Math.toRadians(absDelta.toDouble())
            val radius = distance / turnRad
            if (radius in MIN_RADIUS_M..MAX_RADIUS_M) {
                val lateralG = ((speedMps * speedMps) / radius.toFloat()) / 9.81f
                if (lateralG in 0.05f..2.5f) {
                    maxEstimatedLateralG = maxOf(maxEstimatedLateralG, lateralG)
                }
            }
        }

        if (abs(accumulatedTurnDeg) >= CORNER_COMPLETE_DEG) {
            cornerCount++
            accumulatedTurnDeg = 0f
            inCorner = false
            return true
        }
        return false
    }

    private fun finishCornerIfNeeded() {
        if (inCorner && abs(accumulatedTurnDeg) >= CORNER_COMPLETE_DEG * 0.7f) {
            cornerCount++
        }
        accumulatedTurnDeg = 0f
        inCorner = false
    }

    private fun shortestAngleDeg(delta: Float): Float {
        var d = delta
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    companion object {
        private const val MIN_SPEED_MPS = 4f // ~14 km/h
        private const val NOISE_DEG = 2f
        private const val CORNER_START_DEG = 12f
        private const val CORNER_COMPLETE_DEG = 35f
        private const val MIN_RADIUS_M = 8.0
        private const val MAX_RADIUS_M = 250.0
    }
}
