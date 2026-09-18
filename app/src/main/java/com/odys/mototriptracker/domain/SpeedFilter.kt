package com.odys.mototriptracker.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedFilter @Inject constructor() {
    // Pocket / screen-off fixes are often 20–35 m. 15 m was discarding entire rides.
    private val maxAccuracyMeters = MAX_ACCURACY_METERS

    // Ignore speeds under 3 km/h (0.83 m/s) to prevent GPS drift when stopped
    private val MIN_SPEED_MPS = 0.83f

    fun isValid(sample: GpsSample): Boolean {
        val accuracy = sample.accuracyMeters ?: return false
        return accuracy <= maxAccuracyMeters
    }

    companion object {
        const val MAX_ACCURACY_METERS = 35f
    }

    fun getProcessedSpeed(sample: GpsSample): Float {
        if (!sample.hasSpeed) return 0f

        val speedMps = sample.speedMps

        // Kill ghost speeds (GPS drift while parked)
        return if (speedMps < MIN_SPEED_MPS) 0f else speedMps
    }
}
