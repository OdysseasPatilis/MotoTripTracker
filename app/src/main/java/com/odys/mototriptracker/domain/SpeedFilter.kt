package com.odys.mototriptracker.domain

import android.location.Location
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedFilter @Inject constructor() {
    // Pocket / screen-off fixes are often 20–35 m. 15 m was discarding entire rides.
    private val maxAccuracyMeters = MAX_ACCURACY_METERS

    // Ignore speeds under 3 km/h (0.83 m/s) to prevent GPS drift when stopped
    private val MIN_SPEED_MPS = 0.83f

    fun isValid(location: Location): Boolean {
        // 1. Throw away locations with terrible accuracy
        if (!location.hasAccuracy() || location.accuracy > maxAccuracyMeters) {
            return false
        }
        return true
    }

    companion object {
        const val MAX_ACCURACY_METERS = 35f
    }

    fun getProcessedSpeed(location: Location): Float {
        if (!location.hasSpeed()) return 0f

        val speedMps = location.speed

        // 2. Kill ghost speeds (GPS drift while parked)
        return if (speedMps < MIN_SPEED_MPS) 0f else speedMps
    }
}