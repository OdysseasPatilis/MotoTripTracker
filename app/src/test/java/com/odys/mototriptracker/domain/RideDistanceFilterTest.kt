package com.odys.mototriptracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RideDistanceFilterTest {

    @Test
    fun distanceDelta_capsGeographicJumpAgainstSpeed() {
        // 200 m hop in 1 s at 10 m/s is impossible — reject.
        val delta = RideDistanceFilter.distanceDelta(
            geographicMeters = 200.0,
            speedMps = 10.0,
            timeDeltaSeconds = 1.0
        )
        assertEquals(0.0, delta, 0.01)
    }

    @Test
    fun distanceDelta_acceptsPlausibleStep() {
        val delta = RideDistanceFilter.distanceDelta(
            geographicMeters = 12.0,
            speedMps = 11.0,
            timeDeltaSeconds = 1.0
        )
        assertEquals(12.0, delta, 0.01)
    }

    @Test
    fun averageSpeed_capsAtMaxSpeed() {
        // 47 km in 8 minutes → ~352 km/h raw, capped at 100.
        val avg = RideDistanceFilter.averageSpeedKmh(
            distanceMeters = 47_000.0,
            movingTimeSeconds = 480L,
            maxSpeedKmh = 100.0
        )
        assertEquals(100f, avg, 0.01f)
    }

    @Test
    fun averageSpeed_zeroWhenNoMovingTime() {
        val avg = RideDistanceFilter.averageSpeedKmh(
            distanceMeters = 5_000.0,
            movingTimeSeconds = 0L,
            maxSpeedKmh = 120.0
        )
        assertEquals(0f, avg, 0.01f)
    }
}
