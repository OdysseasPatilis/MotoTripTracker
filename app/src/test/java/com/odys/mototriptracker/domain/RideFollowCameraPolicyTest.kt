package com.odys.mototriptracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RideFollowCameraPolicyTest {

    @Test
    fun cruiseDistance_matchesBaseline() {
        assertEquals(350.0, RideFollowCameraPolicy.cruiseDistanceMeters(0.0), 0.01)
        assertEquals(350.0 + 700.0, RideFollowCameraPolicy.cruiseDistanceMeters(100.0), 0.01)
        assertEquals(350.0 + 180 * 7.0, RideFollowCameraPolicy.cruiseDistanceMeters(200.0), 0.01)
    }

    @Test
    fun lookAhead_growsWithSpeedAndNav() {
        val slow = RideFollowCameraPolicy.lookAheadMeters(20.0, isNavigating = false)
        val fast = RideFollowCameraPolicy.lookAheadMeters(100.0, isNavigating = false)
        val fastNav = RideFollowCameraPolicy.lookAheadMeters(100.0, isNavigating = true)
        assertTrue(fast > slow)
        assertTrue(fastNav > fast)
    }

    @Test
    fun center_usesCourseWhenValid() {
        val ahead = RideFollowCameraPolicy.centerCoordinate(
            riderLat = 37.98,
            riderLng = 23.72,
            courseDegrees = 0f,
            speedKmh = 80.0,
            isNavigating = true,
        )
        assertTrue(ahead.latitude > 37.98)
        assertTrue(abs(ahead.longitude - 23.72) < 0.001)

        val noCourse = RideFollowCameraPolicy.centerCoordinate(
            riderLat = 37.98,
            riderLng = 23.72,
            courseDegrees = -1f,
            speedKmh = 80.0,
            isNavigating = true,
        )
        assertEquals(37.98, noCourse.latitude, 0.0001)
        assertEquals(23.72, noCourse.longitude, 0.0001)
    }

    @Test
    fun cameraDistance_zoomsNearTurnWhenNavigating() {
        val cruise = RideFollowCameraPolicy.cameraDistanceMeters(
            speedKmh = 80.0,
            distanceToNextManeuver = 2000.0,
            isNavigating = true,
            isRecalculating = false,
        )
        val near = RideFollowCameraPolicy.cameraDistanceMeters(
            speedKmh = 80.0,
            distanceToNextManeuver = 80.0,
            isNavigating = true,
            isRecalculating = false,
        )
        assertTrue(near < cruise)

        val recalculating = RideFollowCameraPolicy.cameraDistanceMeters(
            speedKmh = 80.0,
            distanceToNextManeuver = 80.0,
            isNavigating = true,
            isRecalculating = true,
        )
        assertEquals(cruise, recalculating, 0.01)
    }

    @Test
    fun approachWindow_scalesWithSpeed() {
        val city = RideFollowCameraPolicy.approachWindowMeters(40.0)
        val mid = RideFollowCameraPolicy.approachWindowMeters(80.0)
        val hwy = RideFollowCameraPolicy.approachWindowMeters(120.0)
        assertTrue(city in 120.0..150.0)
        assertTrue(abs(mid - 250.0) < 15.0)
        assertTrue(hwy in 350.0..400.0)
    }

    @Test
    fun followFraming_idleCentersOnRider() {
        val framing = RideFollowCameraPolicy.followCameraFraming(
            riderLat = 37.98,
            riderLng = 23.72,
            courseDegrees = 90f,
            speedKmh = 80.0,
            isRiding = false,
            isNavigating = false,
            isRecalculating = false,
            distanceToNextManeuverMeters = null,
        )
        assertEquals(37.98, framing.targetLatitude, 0.0001)
        assertEquals(23.72, framing.targetLongitude, 0.0001)
        assertEquals(14.5f, framing.zoom, 0.01f)
        assertEquals(0f, framing.bearingDegrees, 0.01f)
        assertEquals(0f, framing.tiltDegrees, 0.01f)
    }

    @Test
    fun followFraming_ridingLooksAheadAndTilts() {
        val framing = RideFollowCameraPolicy.followCameraFraming(
            riderLat = 37.98,
            riderLng = 23.72,
            courseDegrees = 0f,
            speedKmh = 80.0,
            isRiding = true,
            isNavigating = true,
            isRecalculating = false,
            distanceToNextManeuverMeters = 80.0,
        )
        assertTrue(framing.targetLatitude > 37.98)
        assertEquals(0f, framing.bearingDegrees, 0.01f)
        assertEquals(55f, framing.tiltDegrees, 0.01f)
        assertTrue(framing.zoom >= 15f)
    }

    @Test
    fun zoomFromDistance_stepsDownAsDistanceGrows() {
        assertEquals(17.5f, RideFollowCameraPolicy.zoomFromDistanceMeters(300.0), 0.01f)
        assertEquals(16.8f, RideFollowCameraPolicy.zoomFromDistanceMeters(500.0), 0.01f)
        assertEquals(15f, RideFollowCameraPolicy.zoomFromDistanceMeters(2000.0), 0.01f)
    }
}
