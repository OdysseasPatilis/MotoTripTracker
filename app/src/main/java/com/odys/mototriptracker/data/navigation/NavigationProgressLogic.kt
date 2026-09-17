package com.odys.mototriptracker.data.navigation

import com.odys.mototriptracker.domain.Geo
import com.odys.mototriptracker.domain.RouteCoordinate

/** Pure progress / arrival / off-route helpers for live navigation. */
object NavigationProgressLogic {
    const val OFF_ROUTE_THRESHOLD_METERS = 80.0
    const val STEP_ADVANCE_METERS = 35.0
    const val APPROACH_ANNOUNCE_METERS = 250.0
    const val ARRIVAL_THRESHOLD_METERS = 45.0
    const val ARRIVAL_REMAINING_MAX_METERS = 120.0
    const val ARRIVAL_DWELL_MS = 2_500L

    data class NearestOnRoute(
        val index: Int,
        val distanceMeters: Double,
        val remainingMeters: Double,
    )

    fun nearestOnRoute(
        latitude: Double,
        longitude: Double,
        route: List<RouteCoordinate>,
    ): NearestOnRoute? {
        if (route.size < 2) return null
        var nearestIndex = 0
        var nearestDistance = Double.MAX_VALUE
        route.forEachIndexed { index, coord ->
            val distance = Geo.distanceMeters(latitude, longitude, coord.latitude, coord.longitude)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }
        var remaining = nearestDistance
        for (index in nearestIndex until route.lastIndex) {
            remaining += Geo.distanceMeters(
                route[index].latitude, route[index].longitude,
                route[index + 1].latitude, route[index + 1].longitude,
            )
        }
        return NearestOnRoute(nearestIndex, nearestDistance, remaining)
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
}
