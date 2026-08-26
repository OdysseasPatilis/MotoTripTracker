package com.odys.mototriptracker.domain

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class RideMomentsCalculatorTest {

    @Test
    fun calculate_includesCoreStatsFromTrip() {
        val trip = TripEntity(
            id = 1,
            startTime = 1_000L,
            endTime = 10_000L,
            distanceMeters = 12_000f,
            movingTime = 600,
            stoppedTime = 120,
            maxSpeed = 142f,
            maxGForce = 1.35f,
            elevationGain = 220f,
            avgSpeed = 72f
        )

        val points = listOf(
            RoutePointEntity(latitude = 0.0, longitude = 0.0, altitude = 10.0, speedMps = 10f, timestamp = 1_000L),
            RoutePointEntity(latitude = 0.0, longitude = 0.0, altitude = 10.0, speedMps = 0.1f, timestamp = 2_000L),
            RoutePointEntity(latitude = 0.0, longitude = 0.0, altitude = 10.0, speedMps = 0.1f, timestamp = 40_000L),
            RoutePointEntity(latitude = 0.0, longitude = 0.0, altitude = 40.0, speedMps = 8f, timestamp = 50_000L),
            RoutePointEntity(latitude = 0.0, longitude = 0.0, altitude = 80.0, speedMps = 8f, timestamp = 60_000L)
        )

        val moments = RideMomentsCalculator.calculate(trip, points).moments
        val titles = moments.map { it.title }.toSet()

        assertTrue(titles.contains("Top speed"))
        assertTrue(titles.contains("Max G"))
        assertTrue(titles.contains("Elevation"))
        assertTrue(titles.contains("Longest stop"))
        assertTrue(titles.contains("Biggest climb"))
    }
}
