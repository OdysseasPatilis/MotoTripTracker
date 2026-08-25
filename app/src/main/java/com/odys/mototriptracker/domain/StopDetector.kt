package com.odys.mototriptracker.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StopDetector @Inject constructor() {
    private var lastUpdateTime = 0L

    // If we request pings every 2s, any gap larger than 4s means we stopped moving 2 meters.
    private val MAX_MOVING_GAP_MS = 4_000L

    // The "Tunnel / Sleep" filter. If the gap is longer than 5 minutes, ignore it entirely.
    // Adjust this based on your needs (e.g., 300,000L = 5 minutes).
    private val MAX_VALID_DELTA_MS = 300_000L
    val speedSmoother = SpeedSmoother()

    fun reset() {
        lastUpdateTime = 0L
    }

    fun updateTimes(
        currentTimeMs: Long,
        onTimeUpdated: (movingMillis: Long, stoppedMillis: Long) -> Unit
    ) {
        if (lastUpdateTime == 0L) {
            lastUpdateTime = currentTimeMs
            return
        }

        val timeDelta = currentTimeMs - lastUpdateTime
        lastUpdateTime = currentTimeMs

        // 1. SAFETY NET: Prevent the massive background spikes
        if (timeDelta <= 0L || timeDelta > MAX_VALID_DELTA_MS) {
            println("StopDetector: Ignored massive time gap of $timeDelta ms")
            return
        }

        // 2. GAP LOGIC:
        if (timeDelta <= MAX_MOVING_GAP_MS) {
            // Continuous pings are coming in. We are driving.
            onTimeUpdated(timeDelta, 0L)
        } else {
            speedSmoother.reset()
            // There was a gap! The GPS went silent because we didn't move 2 meters.
            // We give 2 seconds to "moving" (the time it took to finally move those 2 meters and trigger this ping),
            // and the rest of the silent gap goes to "stopped".
            val movingPortion = 2000L
            val stoppedPortion = timeDelta - movingPortion

            onTimeUpdated(movingPortion, stoppedPortion)
        }
    }
}