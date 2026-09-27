package com.odys.mototriptracker.domain

/**
 * Hold / confirm before publishing a new posted speed limit so a single bad
 * pack/Overpass/Roads hit cannot flash the sign (e.g. 90 → 40 → 90).
 */
object SpeedLimitHoldLogic {
    const val CONFIRM_COUNT = 2
    /** Candidate must agree this long before publish when count alone is slow. */
    const val CONFIRM_HOLD_MS = 3_000L

    data class HoldState(
        val displayedKmh: Int? = null,
        val candidateKmh: Int? = null,
        val candidateCount: Int = 0,
        val candidateSinceMs: Long? = null,
    )

    data class HoldDecision(
        val state: HoldState,
        /** Non-null when the UI should update to this limit. */
        val publishKmh: Int? = null,
    )

    /**
     * @param speedMps GPS speed for [SpeedLimitLogic.limitLooksPlausible] fast-path
     * when the displayed limit is clearly wrong for current speed.
     */
    fun onCandidate(
        state: HoldState,
        candidateKmh: Int,
        speedMps: Float,
        nowMs: Long,
    ): HoldDecision {
        if (state.displayedKmh == null) {
            return HoldDecision(
                state = HoldState(displayedKmh = candidateKmh),
                publishKmh = candidateKmh,
            )
        }
        if (candidateKmh == state.displayedKmh) {
            return HoldDecision(
                state = state.copy(
                    candidateKmh = null,
                    candidateCount = 0,
                    candidateSinceMs = null,
                ),
            )
        }

        // Old limit is implausible at current speed → accept new immediately.
        if (!SpeedLimitLogic.limitLooksPlausible(state.displayedKmh, speedMps) &&
            SpeedLimitLogic.limitLooksPlausible(candidateKmh, speedMps)
        ) {
            return HoldDecision(
                state = HoldState(displayedKmh = candidateKmh),
                publishKmh = candidateKmh,
            )
        }

        if (candidateKmh != state.candidateKmh) {
            return HoldDecision(
                state = state.copy(
                    candidateKmh = candidateKmh,
                    candidateCount = 1,
                    candidateSinceMs = nowMs,
                ),
            )
        }

        val count = state.candidateCount + 1
        val since = state.candidateSinceMs ?: nowMs
        val heldLongEnough = nowMs - since >= CONFIRM_HOLD_MS
        val confirmed = count >= CONFIRM_COUNT || heldLongEnough
        return if (confirmed) {
            HoldDecision(
                state = HoldState(displayedKmh = candidateKmh),
                publishKmh = candidateKmh,
            )
        } else {
            HoldDecision(
                state = state.copy(
                    candidateKmh = candidateKmh,
                    candidateCount = count,
                    candidateSinceMs = since,
                ),
            )
        }
    }
}
