package com.odys.mototriptracker.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotoTravelEstimatorTest {

    @Test
    fun estimate_cutsCarTrafficDelay() {
        val distanceMeters = 10_000.0
        val car = 30 * 60.0
        val estimate = MotoTravelEstimator.estimate(
            distanceMeters = distanceMeters,
            carTravelTimeSeconds = car,
            filterBenefit = 0.5,
        )
        assertTrue(estimate.trafficDelaySeconds > 0)
        assertTrue(estimate.motoTravelTimeSeconds < estimate.carTravelTimeSeconds)
        assertTrue(estimate.motoTravelTimeSeconds > estimate.baselineTravelTimeSeconds)
    }

    @Test
    fun learn_increasesBenefitWhenBeatingCarEta() {
        val result = NavTimingResult(
            distanceMeters = 12_000.0,
            carEstimateSeconds = 40 * 60.0,
            motoEstimateSeconds = 28 * 60.0,
            actualSeconds = 22 * 60.0,
        )
        val learned = MotoTravelEstimator.learnedBenefit(result, currentBenefit = 0.40)
        assertTrue(learned > 0.40)
    }

    @Test
    fun formatMinutes_roundsUpToAtLeastOne() {
        assertEquals("1 min", MotoTravelEstimator.formatMinutes(10.0))
        assertEquals("12 min", MotoTravelEstimator.formatMinutes(12 * 60.0))
    }

    @Test
    fun timingSummary_reportsFasterThanCar() {
        val result = NavTimingResult(
            distanceMeters = 8_000.0,
            carEstimateSeconds = 20 * 60.0,
            motoEstimateSeconds = 14 * 60.0,
            actualSeconds = 12 * 60.0,
        )
        assertTrue(result.summaryLine.contains("faster than car traffic ETA"))
    }
}
