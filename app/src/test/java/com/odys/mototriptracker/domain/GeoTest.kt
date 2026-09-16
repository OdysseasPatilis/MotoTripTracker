package com.odys.mototriptracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GeoTest {

    @Test
    fun distance_zeroForSamePoint() {
        assertEquals(0.0, Geo.distanceMeters(37.98, 23.72, 37.98, 23.72), 0.01)
    }

    @Test
    fun distance_athensApproxOneKmNorth() {
        // ~0.009° latitude ≈ 1 km
        val d = Geo.distanceMeters(37.98, 23.72, 37.989, 23.72)
        assertTrue(d in 900.0..1_100.0)
    }

    @Test
    fun bearing_northIsZero() {
        val bearing = Geo.bearingDegrees(37.98, 23.72, 38.00, 23.72)
        assertTrue(abs(bearing) < 1.0 || abs(bearing - 360.0) < 1.0)
    }

    @Test
    fun bearing_eastIsNinety() {
        val bearing = Geo.bearingDegrees(37.98, 23.72, 37.98, 23.74)
        assertTrue(abs(bearing - 90.0) < 2.0)
    }

    @Test
    fun headingDelta_wrapsAround() {
        assertEquals(20.0, Geo.headingDeltaDegrees(10.0, 350.0), 0.01)
        assertEquals(0.0, Geo.headingDeltaDegrees(0.0, 360.0), 0.01)
    }

    @Test
    fun destination_northMovesLatitudeUp() {
        val (lat, lng) = Geo.destination(37.98, 23.72, bearingDegrees = 0.0, meters = 100.0)
        assertTrue(lat > 37.98)
        assertTrue(abs(lng - 23.72) < 0.0001)
    }
}
