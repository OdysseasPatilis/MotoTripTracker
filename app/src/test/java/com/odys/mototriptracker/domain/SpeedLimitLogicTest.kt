package com.odys.mototriptracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLimitLogicTest {

    @Test
    fun plausibleWhenGpsBelowLimitPlusMargin() {
        // 50 km/h posted; 60 km/h GPS (16.67 m/s) → 60 <= 50+25 → ok
        assertTrue(SpeedLimitLogic.limitLooksPlausible(50, 16.67f))
    }

    @Test
    fun implausibleWhenHighwaySpeedVsResidentialLimit() {
        // 50 km/h pack; ~120 km/h GPS → fall through to Overpass
        assertFalse(SpeedLimitLogic.limitLooksPlausible(50, 33.3f))
    }

    @Test
    fun negativeSpeedAlwaysPlausible() {
        assertTrue(SpeedLimitLogic.limitLooksPlausible(30, -1f))
    }

    @Test
    fun gridKeyStableForNearbyPointsInSameCell() {
        // Build coordinates from the middle of a grid cell so a tiny nudge stays inside.
        val latCell = 18_991L
        val lngCell = 11_863L
        val lat = (latCell + 0.5) / SpeedLimitLogic.GRID_SCALE
        val lng = (lngCell + 0.5) / SpeedLimitLogic.GRID_SCALE
        val a = SpeedLimitLogic.gridKey(lat, lng)
        val b = SpeedLimitLogic.gridKey(lat + 0.0005, lng + 0.0005)
        assertEquals("${latCell}_${lngCell}", a)
        assertEquals(a, b)
    }

    @Test
    fun gridKeyChangesAcrossCellBoundary() {
        val latCell = 18_991L
        val lngCell = 11_863L
        val lat = (latCell + 0.5) / SpeedLimitLogic.GRID_SCALE
        val lng = (lngCell + 0.5) / SpeedLimitLogic.GRID_SCALE
        val a = SpeedLimitLogic.gridKey(lat, lng)
        val b = SpeedLimitLogic.gridKey(lat + 1.0 / SpeedLimitLogic.GRID_SCALE, lng)
        assertTrue(a != b)
    }

    @Test
    fun neighbourKeysExcludeSelfAndHaveEight() {
        val keys = SpeedLimitLogic.neighbourGridKeys(37.9838, 23.7275)
        assertEquals(8, keys.size)
        assertFalse(keys.contains(SpeedLimitLogic.gridKey(37.9838, 23.7275)))
    }

    @Test
    fun shouldQueryWhenNeverQueried() {
        assertTrue(
            SpeedLimitLogic.shouldQuery(
                latitude = 37.98,
                longitude = 23.72,
                lastLat = null,
                lastLng = null,
                lastQueryTimeMs = 0L,
                nowMs = 1_000L,
            )
        )
    }

    @Test
    fun shouldNotQueryWhenCloseAndRecent() {
        assertFalse(
            SpeedLimitLogic.shouldQuery(
                latitude = 37.98001,
                longitude = 23.72001,
                lastLat = 37.98,
                lastLng = 23.72,
                lastQueryTimeMs = 1_000L,
                nowMs = 5_000L,
            )
        )
    }

    @Test
    fun shouldQueryAfterIntervalEvenIfStationary() {
        assertTrue(
            SpeedLimitLogic.shouldQuery(
                latitude = 37.98,
                longitude = 23.72,
                lastLat = 37.98,
                lastLng = 23.72,
                lastQueryTimeMs = 1_000L,
                nowMs = 1_000L + SpeedLimitLogic.MIN_INTERVAL_MS,
            )
        )
    }

    @Test
    fun shouldQueryAfterMovingFarEnough() {
        // ~40 m north at Athens latitude
        assertTrue(
            SpeedLimitLogic.shouldQuery(
                latitude = 37.98036,
                longitude = 23.72,
                lastLat = 37.98,
                lastLng = 23.72,
                lastQueryTimeMs = 1_000L,
                nowMs = 2_000L,
            )
        )
    }
}
