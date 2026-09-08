package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.RoutePolylineFallback
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.data.trip.TripRepository
import com.odys.mototriptracker.util.AppLogger
import javax.inject.Inject

data class TripRouteDetails(
    val trip: TripEntity,
    val routePoints: List<RoutePointEntity>,
    val waypoints: List<RoutePointEntity>,
    /** True when the trail was rebuilt from [TripEntity.encodedRoutePolyline]. */
    val usedPolylineFallback: Boolean = false,
)

class GetTripRouteUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(tripId: Long): TripRouteDetails? {
        val trip = tripRepository.getTrip(tripId) ?: return null
        var points = tripRepository.getRoutePointsForMap(tripId)
        var usedFallback = false

        // Summary can show a route from the encoded polyline even when the
        // ObjectBox point query returns nothing (seen after long background rides).
        if (points.size < 2) {
            val reconstructed = RoutePolylineFallback.reconstructPoints(
                encoded = trip.encodedRoutePolyline,
                startTimeMs = trip.startTime,
                endTimeMs = trip.endTime,
            )
            if (reconstructed.size >= 2) {
                points = reconstructed
                usedFallback = true
                AppLogger.w(
                    AppLogger.Category.PERSISTENCE,
                    "FullRoute fallback to encoded polyline id=$tripId verts=${reconstructed.size}"
                )
            }
        }

        val waypoints = if (usedFallback) {
            emptyList()
        } else {
            tripRepository.getWaypointsForTrip(tripId)
        }

        return TripRouteDetails(
            trip = trip,
            routePoints = points,
            waypoints = waypoints,
            usedPolylineFallback = usedFallback,
        )
    }
}
