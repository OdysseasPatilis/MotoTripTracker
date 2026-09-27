package com.odys.mototriptracker.data.navigation

import com.odys.mototriptracker.domain.RouteCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationProgressLogicTest {

    private fun route(vararg points: Pair<Double, Double>) =
        points.map { RouteCoordinate(it.first, it.second) }

    private fun step(lat: Double, lng: Double, id: String = "s") = NavStep(
        id = id,
        instruction = "Turn",
        distanceMeters = 100.0,
        endLatitude = lat,
        endLongitude = lng,
    )

    @Test
    fun nearestOnRouteReturnsNullForShortRoute() {
        assertNull(NavigationProgressLogic.nearestOnRoute(0.0, 0.0, emptyList()))
        assertNull(NavigationProgressLogic.nearestOnRoute(0.0, 0.0, route(0.0 to 0.0)))
    }

    @Test
    fun remainingIncludesTailOfRoute() {
        // ~111 m per 0.001° latitude
        val coords = route(
            37.9800 to 23.7200,
            37.9810 to 23.7200,
            37.9820 to 23.7200,
        )
        val near = NavigationProgressLogic.nearestOnRoute(37.9800, 23.7200, coords)
        assertNotNull(near)
        // From start: ~111m + ~111m ≈ 222m (+ tiny nearest distance)
        assertTrue(near!!.remainingMeters in 200.0..250.0)
        assertEquals(0, near.index)
    }

    @Test
    fun etaScalesWithRemainingFraction() {
        val eta = NavigationProgressLogic.etaEpochMs(
            remainingMeters = 500.0,
            totalRouteDistanceMeters = 1000.0,
            totalTravelTimeSeconds = 600.0,
            nowMs = 1_000_000L,
            fallbackEtaEpochMs = null,
        )
        assertEquals(1_000_000L + 300_000L, eta)
    }

    @Test
    fun advanceStepWhenNearEndOfCurrent() {
        val steps = listOf(
            step(37.9800, 23.7200, "a"),
            step(37.9810, 23.7200, "b"),
        )
        // Standing on first step end → advance to second
        val next = NavigationProgressLogic.advancedStepIndex(
            latitude = 37.9800,
            longitude = 23.7200,
            steps = steps,
            currentIndex = 0,
        )
        assertEquals(1, next)
    }

    @Test
    fun doesNotAdvancePastLastStep() {
        val steps = listOf(step(37.9800, 23.7200, "only"))
        val next = NavigationProgressLogic.advancedStepIndex(
            latitude = 37.9800,
            longitude = 23.7200,
            steps = steps,
            currentIndex = 0,
        )
        assertEquals(0, next)
    }

    @Test
    fun approachAnnounceWithinThreshold() {
        assertTrue(NavigationProgressLogic.shouldAnnounceApproach(200.0, alreadyApproached = false))
        assertFalse(NavigationProgressLogic.shouldAnnounceApproach(200.0, alreadyApproached = true))
        assertFalse(NavigationProgressLogic.shouldAnnounceApproach(300.0, alreadyApproached = false))
    }

    @Test
    fun arrivalRequiresDwellNearDestination() {
        val t0 = 10_000L
        val start = NavigationProgressLogic.arrivalTick(
            toDestinationMeters = 20.0,
            distanceRemainingMeters = 50.0,
            currentStepIndex = 2,
            stepCount = 3,
            candidateSinceMs = null,
            nowMs = t0,
        )
        assertEquals(t0, start.candidateSinceMs)
        assertFalse(start.shouldComplete)

        val later = NavigationProgressLogic.arrivalTick(
            toDestinationMeters = 20.0,
            distanceRemainingMeters = 50.0,
            currentStepIndex = 2,
            stepCount = 3,
            candidateSinceMs = t0,
            nowMs = t0 + NavigationProgressLogic.ARRIVAL_DWELL_MS,
        )
        assertTrue(later.shouldComplete)

        val left = NavigationProgressLogic.arrivalTick(
            toDestinationMeters = 100.0,
            distanceRemainingMeters = 50.0,
            currentStepIndex = 2,
            stepCount = 3,
            candidateSinceMs = t0,
            nowMs = t0 + 5_000L,
        )
        assertNull(left.candidateSinceMs)
        assertFalse(left.shouldComplete)
    }

    @Test
    fun offRouteHysteresis() {
        assertTrue(NavigationProgressLogic.isOffRoute(90.0))
        assertFalse(NavigationProgressLogic.isOffRoute(50.0))
        assertTrue(NavigationProgressLogic.shouldClearOffRoute(30.0))
        assertFalse(NavigationProgressLogic.shouldClearOffRoute(50.0))
    }

    @Test
    fun segmentProjectionPrefersMidSegmentOverFarVertex() {
        // Horizontal segment ~111 m; rider slightly off to the side at midpoint.
        val coords = route(
            37.9800 to 23.7200,
            37.9810 to 23.7200,
            37.9820 to 23.7200,
        )
        val midLat = 37.9805
        val midLng = 23.7205 // ~40 m east of the road
        val near = NavigationProgressLogic.nearestOnRoute(midLat, midLng, coords)
        assertNotNull(near)
        assertEquals(0, near!!.index)
        assertTrue(near.distanceMeters in 30.0..60.0)
        // Remaining ≈ half of first segment (~55 m) + full second (~111 m) ≈ 166 m
        assertTrue(near.remainingMeters in 140.0..200.0)
    }

    @Test
    fun offRouteDwellRequiresConsecutiveTicks() {
        var ticks = 0
        var flagged = false
        repeat(NavigationProgressLogic.OFF_ROUTE_CONFIRM_TICKS - 1) {
            val d = NavigationProgressLogic.offRouteDwellTick(
                nearestRouteDistanceMeters = 100.0,
                consecutiveOffRouteTicks = ticks,
                currentlyFlaggedOffRoute = flagged,
            )
            ticks = d.consecutiveOffRouteTicks
            flagged = d.isOffRoute
            assertFalse(d.shouldRecalculate)
            assertFalse(flagged)
        }
        val confirmed = NavigationProgressLogic.offRouteDwellTick(
            nearestRouteDistanceMeters = 100.0,
            consecutiveOffRouteTicks = ticks,
            currentlyFlaggedOffRoute = flagged,
        )
        assertTrue(confirmed.isOffRoute)
        assertTrue(confirmed.shouldRecalculate)

        val cleared = NavigationProgressLogic.offRouteDwellTick(
            nearestRouteDistanceMeters = 20.0,
            consecutiveOffRouteTicks = confirmed.consecutiveOffRouteTicks,
            currentlyFlaggedOffRoute = true,
        )
        assertFalse(cleared.isOffRoute)
        assertEquals(0, cleared.consecutiveOffRouteTicks)
    }
}
