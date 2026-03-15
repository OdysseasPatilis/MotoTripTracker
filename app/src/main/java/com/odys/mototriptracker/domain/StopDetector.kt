package com.odys.mototriptracker.domain

class StopDetector {
    private var lastUpdateTime = 0L

    fun updateTimes(
        currentSpeedMps: Float,
        currentTimeMs: Long,
        onTimeUpdated: (movingMillis: Long, stoppedMillis: Long) -> Unit
    ) {
        if (lastUpdateTime == 0L) {
            lastUpdateTime = currentTimeMs
            return
        }

        val timeDelta = currentTimeMs - lastUpdateTime
        lastUpdateTime = currentTimeMs

        if (currentSpeedMps > 0f) {
            onTimeUpdated(timeDelta, 0L) // Added to moving
        } else {
            onTimeUpdated(0L, timeDelta) // Added to stopped
        }
    }
}