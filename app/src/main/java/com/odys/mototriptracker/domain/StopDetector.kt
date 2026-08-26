package com.odys.mototriptracker.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies elapsed time between GPS updates as moving vs stopped using speed,
 * matching the iOS StopDetector.
 *
 * The old gap-based approach (treat >4s silence as stopped) was designed for
 * [setMinUpdateDistanceMeters] and breaks when updates arrive every ~1s or when
 * accuracy filtering creates irregular gaps while riding.
 */
@Singleton
class StopDetector @Inject constructor() {
    private var lastUpdateTime = 0L
    private var hasBaseline = false

    /** Gaps longer than this are ignored (tunnel / sleep / background kill). */
    private val maxValidDeltaMs = 300_000L

    fun reset() {
        lastUpdateTime = 0L
        hasBaseline = false
    }

    /**
     * @param currentTimeMs location timestamp in epoch millis
     * @param isMoving true when filtered speed indicates real motion
     * @param onTimeUpdated (movingMillis, stoppedMillis)
     */
    fun updateTimes(
        currentTimeMs: Long,
        isMoving: Boolean,
        onTimeUpdated: (movingMillis: Long, stoppedMillis: Long) -> Unit
    ) {
        if (!hasBaseline) {
            lastUpdateTime = currentTimeMs
            hasBaseline = true
            return
        }

        val timeDelta = currentTimeMs - lastUpdateTime
        lastUpdateTime = currentTimeMs

        if (timeDelta <= 0L || timeDelta > maxValidDeltaMs) {
            return
        }

        if (isMoving) {
            onTimeUpdated(timeDelta, 0L)
        } else {
            onTimeUpdated(0L, timeDelta)
        }
    }
}
