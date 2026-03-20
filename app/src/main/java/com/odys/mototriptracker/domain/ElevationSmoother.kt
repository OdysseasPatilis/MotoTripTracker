package com.odys.mototriptracker.domain

class ElevationSmoother(private val alpha: Float = 0.15f) {
    private var smoothedAltitude: Double? = null
    private var referenceAltitude: Double? = null

    // Call this with location.altitude every time you get a ping
    fun calculateGain(rawAltitude: Double): Double {
        // 1. Apply a Low-Pass Filter to flatten the jitter
        val currentSmoothed = if (smoothedAltitude == null) {
            rawAltitude
        } else {
            (alpha * rawAltitude) + ((1 - alpha) * smoothedAltitude!!)
        }
        smoothedAltitude = currentSmoothed

        // 2. Initialize our reference point
        if (referenceAltitude == null) {
            referenceAltitude = currentSmoothed
            return 0.0
        }

        // 3. Only count gain if we've legitimately climbed > 2.5 meters
        // from our last known solid reference point.
        val diff = currentSmoothed - referenceAltitude!!

        return when {
            diff > 2.5 -> {
                // We undeniably went up. Count the gain, and reset our baseline.
                referenceAltitude = currentSmoothed
                diff
            }
            diff < -2.5 -> {
                // We undeniably went down. Reset the baseline so we don't
                // accidentally count the recovery from a downhill as a climb.
                referenceAltitude = currentSmoothed
                0.0
            }
            else -> {
                // We are hovering in the "noise zone". Do nothing.
                0.0
            }
        }
    }
}