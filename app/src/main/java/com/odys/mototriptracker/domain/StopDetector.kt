package com.odys.mototriptracker.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies elapsed time between GPS updates as moving vs stopped using speed.
 *
 * Gaps longer than [GAP_AS_MOVING_MS] (lost GPS while still riding) are counted as
 * **moving** — the rider was typically still covering ground during the blackout.
 * Only short, regular updates use the instantaneous speed flag for stopped vs moving.
 */
@Singleton
class StopDetector @Inject constructor() {
    private var lastUpdateTime = 0L
    private var hasBaseline = false

    fun reset() {
        lastUpdateTime = 0L
        hasBaseline = false
    }

    /**
     * @param currentTimeMs location timestamp in epoch millis
     * @param isMoving true when filtered speed indicates real motion
     * @param onTimeUpdated (movingMillis, stoppedMillis) — only invoked when the delta is accepted
     * @return true when the delta was accepted and applied
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

        if (timeDelta <= 0L || timeDelta > MAX_VALID_DELTA_MS) {
            return false
        }

        // Lost / sparse GPS while the ride is still active → count as moving.
        if (timeDelta > GAP_AS_MOVING_MS || isMoving) {
            onTimeUpdated(timeDelta, 0L)
        } else {
            onTimeUpdated(0L, timeDelta)
        }
        return true
    }

    companion object {
        /** 20 minutes — ignore multi-hour kills / abandoned sessions. */
        const val MAX_VALID_DELTA_MS = 1_200_000L

        /** Above normal 1 Hz GPS spacing → treat the hole as moving time. */
        const val GAP_AS_MOVING_MS = 4_000L
    }
}
