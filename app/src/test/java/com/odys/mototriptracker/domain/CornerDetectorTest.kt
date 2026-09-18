package com.odys.mototriptracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerDetectorTest {

    @Test
    fun ignoresSlowMovement() {
        val detector = CornerDetector()
        assertFalse(detector.onSample(37.9800, 23.7200, bearingDeg = 0f, speedMps = 2f))
        assertFalse(detector.onSample(37.98005, 23.7200, bearingDeg = 20f, speedMps = 2f))
        assertEquals(0, detector.cornerCount)
    }

    @Test
    fun countsCornerAfterEnoughHeadingChange() {
        val detector = CornerDetector()
        val speed = 10f
        assertFalse(detector.onSample(37.98000, 23.7200, 0f, speed))
        assertFalse(detector.onSample(37.98005, 23.7200, 12f, speed))
        assertFalse(detector.onSample(37.98010, 23.72005, 24f, speed))
        val completed = detector.onSample(37.98015, 23.72012, 36f, speed)
        assertTrue(completed)
        assertEquals(1, detector.cornerCount)
    }

    @Test
    fun reverseDirectionFinishesPartialCorner() {
        val detector = CornerDetector()
        val speed = 10f
        detector.onSample(37.98000, 23.7200, 0f, speed)
        detector.onSample(37.98005, 23.7200, 15f, speed)
        detector.onSample(37.98010, 23.7200, 40f, speed)
        detector.onSample(37.98015, 23.7199, 10f, speed)
        assertTrue(detector.cornerCount >= 1)
    }

    @Test
    fun resetClearsState() {
        val detector = CornerDetector()
        val speed = 10f
        detector.onSample(37.98000, 23.7200, 0f, speed)
        detector.onSample(37.98005, 23.7200, 12f, speed)
        detector.onSample(37.98010, 23.72005, 24f, speed)
        detector.onSample(37.98015, 23.72012, 36f, speed)
        assertEquals(1, detector.cornerCount)

        detector.reset()
        assertEquals(0, detector.cornerCount)
        assertEquals(0f, detector.maxEstimatedLateralG, 0.001f)
        assertFalse(detector.onSample(37.98000, 23.7200, 0f, speed))
    }

    @Test
    fun estimatesLateralGOnTightTurn() {
        val detector = CornerDetector()
        val speed = 15f
        detector.onSample(37.98000, 23.72000, 0f, speed)
        detector.onSample(37.98003, 23.72002, 8f, speed)
        detector.onSample(37.98006, 23.72005, 16f, speed)
        detector.onSample(37.98009, 23.72010, 28f, speed)
        detector.onSample(37.98012, 23.72016, 40f, speed)
        assertTrue(
            "expected some lateral G, got ${detector.maxEstimatedLateralG}",
            detector.maxEstimatedLateralG > 0f
        )
    }
}
