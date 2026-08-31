package com.odys.mototriptracker.domain

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StopDetectorTest {

    private lateinit var detector: StopDetector
    private var movingAccum = 0L
    private var stoppedAccum = 0L

    @Before
    fun setUp() {
        detector = StopDetector()
        movingAccum = 0L
        stoppedAccum = 0L
    }

    @Test
    fun movingSpeed_accumulatesMovingTime() {
        detector.updateTimes(1_000L, isMoving = true, ::accumulate)
        detector.updateTimes(2_000L, isMoving = true, ::accumulate)
        detector.updateTimes(3_000L, isMoving = true, ::accumulate)

        assertEquals(2_000L, movingAccum)
        assertEquals(0L, stoppedAccum)
    }

    @Test
    fun zeroSpeed_shortDelta_accumulatesStoppedTime() {
        detector.updateTimes(1_000L, isMoving = false, ::accumulate)
        detector.updateTimes(2_000L, isMoving = false, ::accumulate)
        detector.updateTimes(3_500L, isMoving = false, ::accumulate)

        assertEquals(0L, movingAccum)
        assertEquals(2_500L, stoppedAccum)
    }

    @Test
    fun gpsGap_countsAsMovingEvenIfCurrentlyStopped() {
        detector.updateTimes(0L, isMoving = true, ::accumulate)
        // 10 s hole — lost GPS while riding; count as moving.
        val accepted = detector.updateTimes(10_000L, isMoving = false, ::accumulate)

        assertEquals(true, accepted)
        assertEquals(10_000L, movingAccum)
        assertEquals(0L, stoppedAccum)
    }

    @Test
    fun mixedMotion_splitsCorrectly() {
        detector.updateTimes(0L, isMoving = true, ::accumulate)
        detector.updateTimes(1_000L, isMoving = true, ::accumulate)   // +1000 moving
        detector.updateTimes(3_000L, isMoving = false, ::accumulate)  // +2000 stopped (short)
        detector.updateTimes(4_000L, isMoving = true, ::accumulate)   // +1000 moving

        assertEquals(2_000L, movingAccum)
        assertEquals(2_000L, stoppedAccum)
    }

    @Test
    fun moderateGap_under20Min_counts() {
        detector.updateTimes(0L, isMoving = true, ::accumulate)
        val accepted = detector.updateTimes(400_000L, isMoving = true, ::accumulate)

        assertEquals(true, accepted)
        assertEquals(400_000L, movingAccum)
        assertEquals(0L, stoppedAccum)
    }

    @Test
    fun largeGap_over20Min_isIgnored() {
        detector.updateTimes(0L, isMoving = true, ::accumulate)
        val accepted = detector.updateTimes(1_300_000L, isMoving = true, ::accumulate)

        assertEquals(false, accepted)
        assertEquals(0L, movingAccum)
        assertEquals(0L, stoppedAccum)
    }

    @Test
    fun baseline_returnsFalseAndDoesNotAccumulate() {
        val accepted = detector.updateTimes(1_000L, isMoving = true, ::accumulate)
        assertEquals(false, accepted)
        assertEquals(0L, movingAccum)
    }

    @Test
    fun reset_clearsBaseline() {
        detector.updateTimes(0L, isMoving = true, ::accumulate)
        detector.updateTimes(1_000L, isMoving = true, ::accumulate)
        assertEquals(1_000L, movingAccum)

        detector.reset()
        movingAccum = 0L
        detector.updateTimes(10_000L, isMoving = true, ::accumulate)
        detector.updateTimes(11_000L, isMoving = true, ::accumulate)

        assertEquals(1_000L, movingAccum)
    }

    private fun accumulate(moving: Long, stopped: Long) {
        movingAccum += moving
        stoppedAccum += stopped
    }
}
