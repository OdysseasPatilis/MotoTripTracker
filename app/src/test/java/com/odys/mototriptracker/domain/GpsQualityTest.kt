package com.odys.mototriptracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsQualityTest {

    @Test
    fun fromAccuracy_matchesIosBuckets() {
        assertEquals(GpsQuality.UNKNOWN, GpsQuality.fromAccuracyMeters(null))
        assertEquals(GpsQuality.UNKNOWN, GpsQuality.fromAccuracyMeters(0f))
        assertEquals(GpsQuality.EXCELLENT, GpsQuality.fromAccuracyMeters(5f))
        assertEquals(GpsQuality.GOOD, GpsQuality.fromAccuracyMeters(10f))
        assertEquals(GpsQuality.FAIR, GpsQuality.fromAccuracyMeters(20f))
        assertEquals(GpsQuality.POOR, GpsQuality.fromAccuracyMeters(21f))
    }

    @Test
    fun barCount_matchesIos() {
        assertEquals(4, GpsQuality.EXCELLENT.barCount)
        assertEquals(3, GpsQuality.GOOD.barCount)
        assertEquals(2, GpsQuality.FAIR.barCount)
        assertEquals(1, GpsQuality.POOR.barCount)
        assertEquals(0, GpsQuality.UNKNOWN.barCount)
    }
}
