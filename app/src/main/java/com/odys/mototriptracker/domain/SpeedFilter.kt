package com.odys.mototriptracker.domain

import android.location.Location
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedFilter @Inject constructor() {
    // 15 meters is a good threshold for a motorcycle on a road
    private val MIN_ACCURACY_METERS = 15f

    // Ignore speeds under 3 km/h (0.83 m/s) to prevent GPS drift when stopped
    private val MIN_SPEED_MPS = 0.83f

    fun isValid(location: Location): Boolean {
        // 1. Throw away locations with terrible accuracy
        if (!location.hasAccuracy() || location.accuracy > MIN_ACCURACY_METERS) {
            return false
        }
        return true
    }

    fun getProcessedSpeed(location: Location): Float {
        if (!location.hasSpeed()) return 0f

        val speedMps = location.speed

        // 2. Kill ghost speeds (GPS drift while parked)
        return if (speedMps < MIN_SPEED_MPS) 0f else speedMps
    }
}