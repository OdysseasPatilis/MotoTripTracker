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

    /** Gaps longer than this are ignored (app kill / multi-hour pause without GPS). */
    private val maxValidDeltaMs = MAX_VALID_DELTA_MS

    fun reset() {
        lastUpdateTime = 0L
        hasBaseline = false
    }

    /**
     * @param currentTimeMs location timestamp in epoch millis
     * @param isMoving true when filtered speed indicates real motion
     * @param onTimeUpdated (movingMillis, stoppedMillis) — only invoked when the delta is accepted
     * @return true when the delta was accepted and applied (or was the first baseline sample)
     */
    fun updateTimes(
        currentTimeMs: Long,
        isMoving: Boolean,
        onTimeUpdated: (movingMillis: Long, stoppedMillis: Long) -> Unit
    ): Boolean {
        if (!hasBaseline) {
            lastUpdateTime = currentTimeMs
            hasBaseline = true
            return false
        }

        val timeDelta = currentTimeMs - lastUpdateTime
        lastUpdateTime = currentTimeMs

        if (timeDelta <= 0L || timeDelta > maxValidDeltaMs) {
            return false
        }

        if (isMoving) {
            onTimeUpdated(timeDelta, 0L)
        } else {
            onTimeUpdated(0L, timeDelta)
        }
        return true
    }

    companion object {
        /** 20 minutes — matches iOS `maxValidDeltaSeconds`. */
        const val MAX_VALID_DELTA_MS = 1_200_000L
    }
}
