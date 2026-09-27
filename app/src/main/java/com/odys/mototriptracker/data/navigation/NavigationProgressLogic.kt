package com.odys.mototriptracker.data.navigation

import com.odys.mototriptracker.domain.Geo
import com.odys.mototriptracker.domain.RouteCoordinate
import kotlin.math.min

/** Pure progress / arrival / off-route helpers for live navigation. */
object NavigationProgressLogic {
    const val OFF_ROUTE_THRESHOLD_METERS = 80.0
    const val STEP_ADVANCE_METERS = 35.0
    const val APPROACH_ANNOUNCE_METERS = 250.0
    const val ARRIVAL_THRESHOLD_METERS = 45.0
    const val ARRIVAL_REMAINING_MAX_METERS = 120.0
    const val ARRIVAL_DWELL_MS = 2_500L
    /** Consecutive off-route GPS ticks before flagging / recalculating. */
    const val OFF_ROUTE_CONFIRM_TICKS = 3

    data class NearestOnRoute(
        /** Index of the segment start vertex (0 .. route.lastIndex-1). */
        val index: Int,
        val distanceMeters: Double,
        val remainingMeters: Double,
    )

    /**
     * Projects [latitude]/[longitude] onto the nearest route segment
     * (not just the nearest vertex) and returns remaining distance along the polyline.
     */
    fun nearestOnRoute(
        latitude: Double,
        longitude: Double,
        route: List<RouteCoordinate>,
    ): NearestOnRoute? {
        if (route.size < 2) return null
        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        var bestT = 0.0

        for (index in 0 until route.lastIndex) {
            val a = route[index]
            val b = route[index + 1]
            val projected = projectOntoSegment(
                latitude, longitude,
                a.latitude, a.longitude,
                b.latitude, b.longitude,
            )
            if (projected.distanceMeters < bestDistance) {
                bestDistance = projected.distanceMeters
                bestIndex = index
                bestT = projected.t
            }
        }

        val a = route[bestIndex]
        val b = route[bestIndex + 1]
        val segmentLength = Geo.distanceMeters(
            a.latitude, a.longitude, b.latitude, b.longitude,
        )
        var remaining = segmentLength * (1.0 - bestT).coerceIn(0.0, 1.0)
        for (index in (bestIndex + 1) until route.lastIndex) {
            remaining += Geo.distanceMeters(
                route[index].latitude, route[index].longitude,
                route[index + 1].latitude, route[index + 1].longitude,
            )
        }
        return NearestOnRoute(bestIndex, bestDistance, remaining)
    }

    data class SegmentProjection(val t: Double, val distanceMeters: Double)

    /** [t] in [0,1] along A→B; distance from point to the clamped projection. */
    fun projectOntoSegment(
        lat: Double,
        lng: Double,
        aLat: Double,
        aLng: Double,
        bLat: Double,
        bLng: Double,
    ): SegmentProjection {
        val abLat = bLat - aLat
        val abLng = bLng - aLng
        val abLenSq = abLat * abLat + abLng * abLng
        if (abLenSq < 1e-18) {
            return SegmentProjection(
                t = 0.0,
                distanceMeters = Geo.distanceMeters(lat, lng, aLat, aLng),
            )
        }
        val apLat = lat - aLat
        val apLng = lng - aLng
        val t = ((apLat * abLat + apLng * abLng) / abLenSq).coerceIn(0.0, 1.0)
        val projLat = aLat + t * abLat
        val projLng = aLng + t * abLng
        return SegmentProjection(
            t = t,
            distanceMeters = Geo.distanceMeters(lat, lng, projLat, projLng),
        )
    }

    fun etaEpochMs(
        remainingMeters: Double,
        totalRouteDistanceMeters: Double,
        totalTravelTimeSeconds: Double,
        nowMs: Long,
        fallbackEtaEpochMs: Long?,
    ): Long? {
        if (totalRouteDistanceMeters <= 0 || totalTravelTimeSeconds <= 0) return fallbackEtaEpochMs
        val fraction = (remainingMeters / totalRouteDistanceMeters).coerceIn(0.0, 1.0)
        return nowMs + (totalTravelTimeSeconds * fraction * 1000).toLong()
    }

    /** Returns the advanced step index (may equal [currentIndex]). */
    fun advancedStepIndex(
        latitude: Double,
        longitude: Double,
        steps: List<NavStep>,
        currentIndex: Int,
    ): Int {
        if (steps.isEmpty()) return currentIndex
        var index = currentIndex.coerceIn(0, steps.lastIndex)
        while (index < steps.size) {
            val candidate = steps[index]
            val distance = Geo.distanceMeters(
                latitude, longitude,
                candidate.endLatitude, candidate.endLongitude,
            )
            if (distance <= STEP_ADVANCE_METERS && index < steps.lastIndex) {
                index++
                continue
            }
            break
        }
        return index
    }

    fun distanceToStepEnd(
        latitude: Double,
        longitude: Double,
        step: NavStep,
    ): Double = Geo.distanceMeters(latitude, longitude, step.endLatitude, step.endLongitude)

    fun shouldAnnounceApproach(distanceMeters: Double, alreadyApproached: Boolean): Boolean =
        distanceMeters <= APPROACH_ANNOUNCE_METERS && !alreadyApproached

    data class ArrivalTick(
        val candidateSinceMs: Long?,
        val shouldComplete: Boolean,
    )

    fun arrivalTick(
        toDestinationMeters: Double,
        distanceRemainingMeters: Double,
        currentStepIndex: Int,
        stepCount: Int,
        candidateSinceMs: Long?,
        nowMs: Long,
    ): ArrivalTick {
        val nearDestination = toDestinationMeters <= ARRIVAL_THRESHOLD_METERS
        val nearRouteEnd = distanceRemainingMeters <= ARRIVAL_REMAINING_MAX_METERS
        val onFinalStep = stepCount > 0 && currentStepIndex >= stepCount - 1

        if (!(nearDestination && (nearRouteEnd || onFinalStep))) {
            return ArrivalTick(candidateSinceMs = null, shouldComplete = false)
        }
        val since = candidateSinceMs ?: nowMs
        val shouldComplete = nowMs - since >= ARRIVAL_DWELL_MS
        return ArrivalTick(candidateSinceMs = since, shouldComplete = shouldComplete)
    }

    fun isOffRoute(nearestRouteDistanceMeters: Double): Boolean =
        nearestRouteDistanceMeters > OFF_ROUTE_THRESHOLD_METERS

    fun shouldClearOffRoute(nearestRouteDistanceMeters: Double): Boolean =
        nearestRouteDistanceMeters <= OFF_ROUTE_THRESHOLD_METERS / 2.0

    data class OffRouteDwell(
        val consecutiveOffRouteTicks: Int,
        val isOffRoute: Boolean,
        val shouldRecalculate: Boolean,
    )

    /**
     * Requires [OFF_ROUTE_CONFIRM_TICKS] consecutive off-route samples before
     * confirming off-route / allowing recalculation. Clears when hysteresis
     * says we are back on route (≤ half threshold).
     */
    fun offRouteDwellTick(
        nearestRouteDistanceMeters: Double,
        consecutiveOffRouteTicks: Int,
        currentlyFlaggedOffRoute: Boolean,
        confirmTicks: Int = OFF_ROUTE_CONFIRM_TICKS,
    ): OffRouteDwell {
        if (shouldClearOffRoute(nearestRouteDistanceMeters)) {
            return OffRouteDwell(
                consecutiveOffRouteTicks = 0,
                isOffRoute = false,
                shouldRecalculate = false,
            )
        }
        if (isOffRoute(nearestRouteDistanceMeters)) {
            val next = min(consecutiveOffRouteTicks + 1, confirmTicks + 5)
            val confirmed = next >= confirmTicks
            return OffRouteDwell(
                consecutiveOffRouteTicks = next,
                isOffRoute = confirmed || currentlyFlaggedOffRoute,
                shouldRecalculate = confirmed,
            )
        }
        // Between clear and enter thresholds: keep flag, reset streak.
        return OffRouteDwell(
            consecutiveOffRouteTicks = 0,
            isOffRoute = currentlyFlaggedOffRoute,
            shouldRecalculate = false,
        )
    }
}
