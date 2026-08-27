package com.odys.mototriptracker.domain

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideMomentsCalculatorTest {

    @Test
    fun calculate_tellsStoriesNotDuplicateStats() {
        val trip = TripEntity(
            id = 1,
            startTime = 1_720_000_000_000L, // afternoon-ish epoch ms
            endTime = 1_720_000_600_000L,
            distanceMeters = 22_000f,
            movingTime = 1_800,
            stoppedTime = 200,
            maxSpeed = 142f,
            maxGForce = 1.35f,
            elevationGain = 220f,
            avgSpeed = 72f,
            cornerCount = 18
        )

        // Build a short route story: stop, climb, speed spike, acceleration.
        val points = mutableListOf<RoutePointEntity>()
        var t = 1_000L
        // moving
        points += RoutePointEntity(id = 1, latitude = 37.98, longitude = 23.72, altitude = 50.0, speedMps = 12f, timestamp = t)
        t += 2_000
        // long stop (~30s)
        points += RoutePointEntity(id = 2, latitude = 37.9801, longitude = 23.7201, altitude = 50.0, speedMps = 0.1f, timestamp = t)
        t += 30_000
        points += RoutePointEntity(id = 3, latitude = 37.9801, longitude = 23.7201, altitude = 50.0, speedMps = 0.1f, timestamp = t)
        t += 1_000
        // climb > 12m
        points += RoutePointEntity(id = 4, latitude = 37.981, longitude = 23.721, altitude = 55.0, speedMps = 8f, timestamp = t)
        t += 2_000
        points += RoutePointEntity(id = 5, latitude = 37.982, longitude = 23.722, altitude = 70.0, speedMps = 8f, timestamp = t)
        t += 2_000
        points += RoutePointEntity(id = 6, latitude = 37.983, longitude = 23.723, altitude = 85.0, speedMps = 8f, timestamp = t)
        t += 1_000
        // peak speed
        points += RoutePointEntity(id = 7, latitude = 37.984, longitude = 23.724, altitude = 80.0, speedMps = 40f, timestamp = t)
        t += 500
        // hard pull (Δv over ~0.5s)
        points += RoutePointEntity(id = 8, latitude = 37.9845, longitude = 23.7245, altitude = 80.0, speedMps = 28f, timestamp = t)
        t += 500
        points += RoutePointEntity(id = 9, latitude = 37.985, longitude = 23.725, altitude = 80.0, speedMps = 36f, timestamp = t)

        val moments = RideMomentsCalculator.calculate(trip, points).moments
        val titles = moments.map { it.title }.toSet()
        val ids = moments.map { it.id }.toSet()

        assertTrue(moments.isNotEmpty())
        assertTrue(moments.size <= 6)
        assertTrue(ids.contains("peak-speed") || titles.contains("Peak rush"))
        assertTrue(ids.contains("longest-stop") || titles.contains("Longest pause"))
        assertTrue(ids.contains("biggest-climb") || titles.contains("Biggest climb"))

        // Must not be a second stats grid (iOS contract).
        assertFalse(titles.contains("Max G"))
        assertFalse(titles.contains("Corners"))
        assertFalse(titles.contains("Moving pace"))
        assertFalse(titles.contains("Top speed"))
        assertTrue(moments.all { it.iconKey.isNotBlank() })
    }
}
