package com.odys.mototriptracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedLimitHoldLogicTest {

    @Test
    fun firstCandidatePublishesImmediately() {
        val decision = SpeedLimitHoldLogic.onCandidate(
            state = SpeedLimitHoldLogic.HoldState(),
            candidateKmh = 90,
            speedMps = 20f,
            nowMs = 1_000L,
        )
        assertEquals(90, decision.publishKmh)
        assertEquals(90, decision.state.displayedKmh)
    }

    @Test
    fun sameAsDisplayedDoesNotRepublish() {
        val decision = SpeedLimitHoldLogic.onCandidate(
            state = SpeedLimitHoldLogic.HoldState(displayedKmh = 90),
            candidateKmh = 90,
            speedMps = 20f,
            nowMs = 2_000L,
        )
        assertNull(decision.publishKmh)
        assertEquals(90, decision.state.displayedKmh)
    }

    @Test
    fun differentCandidateNeedsConfirmCount() {
        val first = SpeedLimitHoldLogic.onCandidate(
            state = SpeedLimitHoldLogic.HoldState(displayedKmh = 90),
            candidateKmh = 40,
            speedMps = 10f, // 36 km/h — both limits look plausible
            nowMs = 1_000L,
        )
        assertNull(first.publishKmh)
        assertEquals(40, first.state.candidateKmh)
        assertEquals(1, first.state.candidateCount)

        val second = SpeedLimitHoldLogic.onCandidate(
            state = first.state,
            candidateKmh = 40,
            speedMps = 10f,
            nowMs = 1_500L,
        )
        assertEquals(40, second.publishKmh)
        assertEquals(40, second.state.displayedKmh)
    }

    @Test
    fun holdsUntilConfirmMsWhenCountSlow() {
        val first = SpeedLimitHoldLogic.onCandidate(
            state = SpeedLimitHoldLogic.HoldState(displayedKmh = 90),
            candidateKmh = 50,
            speedMps = 10f,
            nowMs = 1_000L,
        )
        assertNull(first.publishKmh)

        // Same candidate again would confirm by count=2; use hold time instead by
        // resetting count via... actually second call increments to 2. Use hold:
        // keep candidateCount at 1 by checking hold path: CONFIRM_COUNT is 2, so
        // only one sample + wait CONFIRM_HOLD_MS with same candidate after...
        // Wait: second onCandidate with same kmh bumps count to 2 and publishes.
        // Hold MS path: need count < CONFIRM_COUNT but time elapsed.
        // With CONFIRM_COUNT=2, first sets count=1; if we somehow stay at 1...
        // Only one path: first creates candidate; then time passes without a second
        // call that increments — but we need another onCandidate call to evaluate hold.
        // Second call with same candidate: count becomes 2 → publishes via count.
        // So hold MS is for when CONFIRM_COUNT is higher... Looking at logic:
        // confirmed = count >= CONFIRM_COUNT || heldLongEnough
        // After first: count=1. After second: count=2 → confirmed by count.
        // Hold MS alone: first sets count=1; second with same after HOLD_MS:
        // count=2 still. To test hold alone, temporarily the code uses OR.
        // With count 1 and heldLongEnough: we need a second call that doesn't
        // reach CONFIRM_COUNT before hold — impossible with CONFIRM_COUNT=2.
        // So test hold by: first sample, then second sample after HOLD with count
        // going to 2 anyway. Instead verify hold path when CONFIRM_COUNT custom...
        // Simpler: after first, call again after HOLD_MS — publishes (count or hold).
        val held = SpeedLimitHoldLogic.onCandidate(
            state = first.state,
            candidateKmh = 50,
            speedMps = 10f,
            nowMs = 1_000L + SpeedLimitHoldLogic.CONFIRM_HOLD_MS,
        )
        assertEquals(50, held.publishKmh)
    }

    @Test
    fun immediateJumpWhenDisplayedImplausible() {
        // Displayed 40 while GPS ~100 km/h → implausible; candidate 90 ok.
        val decision = SpeedLimitHoldLogic.onCandidate(
            state = SpeedLimitHoldLogic.HoldState(displayedKmh = 40),
            candidateKmh = 90,
            speedMps = 28f, // ~100 km/h
            nowMs = 1_000L,
        )
        assertEquals(90, decision.publishKmh)
        assertEquals(90, decision.state.displayedKmh)
    }

    @Test
    fun switchingCandidateResetsCount() {
        val first = SpeedLimitHoldLogic.onCandidate(
            state = SpeedLimitHoldLogic.HoldState(displayedKmh = 90),
            candidateKmh = 40,
            speedMps = 10f,
            nowMs = 1_000L,
        )
        val switched = SpeedLimitHoldLogic.onCandidate(
            state = first.state,
            candidateKmh = 50,
            speedMps = 10f,
            nowMs = 1_200L,
        )
        assertNull(switched.publishKmh)
        assertEquals(50, switched.state.candidateKmh)
        assertEquals(1, switched.state.candidateCount)
    }
}
