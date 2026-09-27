package com.odys.mototriptracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadSnapLogicTest {

    @Test
    fun bufferCapsAtMaxPoints() {
        var buffer = emptyList<RoadSnapLogic.GpsPoint>()
        repeat(RoadSnapLogic.MAX_BUFFER_POINTS + 5) { i ->
            buffer = RoadSnapLogic.appendPoint(buffer, 37.0 + i * 0.0001, 23.0, i * 1000L)
        }
        assertEquals(RoadSnapLogic.MAX_BUFFER_POINTS, buffer.size)
        assertEquals(37.0 + (RoadSnapLogic.MAX_BUFFER_POINTS + 4) * 0.0001, buffer.last().latitude, 1e-9)
    }

    @Test
    fun shouldSnapWhenNeverSnapped() {
        val buffer = listOf(RoadSnapLogic.GpsPoint(37.98, 23.72, 1_000L))
        assertTrue(
            RoadSnapLogic.shouldSnap(
                buffer = buffer,
                lastSnapLat = null,
                lastSnapLng = null,
                lastSnapRequestMs = 0L,
                nowMs = 1_000L,
            )
        )
    }

    @Test
    fun shouldNotSnapWhenCloseAndRecent() {
        val buffer = listOf(RoadSnapLogic.GpsPoint(37.98001, 23.72001, 2_000L))
        assertFalse(
            RoadSnapLogic.shouldSnap(
                buffer = buffer,
                lastSnapLat = 37.98,
                lastSnapLng = 23.72,
                lastSnapRequestMs = 1_000L,
                nowMs = 2_000L,
            )
        )
    }

    @Test
    fun shouldSnapAfterInterval() {
        val buffer = listOf(RoadSnapLogic.GpsPoint(37.98, 23.72, 10_000L))
        assertTrue(
            RoadSnapLogic.shouldSnap(
                buffer = buffer,
                lastSnapLat = 37.98,
                lastSnapLng = 23.72,
                lastSnapRequestMs = 1_000L,
                nowMs = 1_000L + RoadSnapLogic.MIN_SNAP_INTERVAL_MS,
            )
        )
    }

    @Test
    fun shouldSnapAfterMovingFarEnough() {
        // ~40 m north
        val buffer = listOf(RoadSnapLogic.GpsPoint(37.98036, 23.72, 2_000L))
        assertTrue(
            RoadSnapLogic.shouldSnap(
                buffer = buffer,
                lastSnapLat = 37.98,
                lastSnapLng = 23.72,
                lastSnapRequestMs = 1_000L,
                nowMs = 2_000L,
            )
        )
    }

    @Test
    fun freshSnapWithinWindow() {
        val snap = SnappedRoad(
            latitude = 37.98,
            longitude = 23.72,
            placeId = "ChIJ",
            rawLatitude = 37.981,
            rawLongitude = 23.721,
            snappedAtMs = 5_000L,
        )
        assertTrue(RoadSnapLogic.isFresh(snap, nowMs = 5_000L + RoadSnapLogic.FRESH_SNAP_MS))
        assertFalse(RoadSnapLogic.isFresh(snap, nowMs = 5_000L + RoadSnapLogic.FRESH_SNAP_MS + 1))
        assertFalse(RoadSnapLogic.isFresh(null, nowMs = 5_000L))
    }

    @Test
    fun pickLatestPrefersMatchingOriginalIndex() {
        val raw = listOf(
            RoadSnapLogic.GpsPoint(37.9800, 23.7200, 1L),
            RoadSnapLogic.GpsPoint(37.9810, 23.7200, 2L),
        )
        val snap = RoadSnapLogic.pickLatestSnap(
            snappedPoints = listOf(37.9801 to 23.7200, 37.9811 to 23.7200),
            placeIds = listOf("a", "b"),
            originalIndices = listOf(0, 1),
            rawPath = raw,
            nowMs = 99L,
        )
        assertNotNull(snap)
        assertEquals("b", snap!!.placeId)
        assertEquals(37.9811, snap.latitude, 1e-6)
        assertEquals(99L, snap.snappedAtMs)
    }

    @Test
    fun pickLatestReturnsNullWhenEmpty() {
        assertNull(
            RoadSnapLogic.pickLatestSnap(
                snappedPoints = emptyList(),
                placeIds = emptyList(),
                originalIndices = emptyList(),
                rawPath = listOf(RoadSnapLogic.GpsPoint(0.0, 0.0, 1L)),
                nowMs = 1L,
            )
        )
    }
}
