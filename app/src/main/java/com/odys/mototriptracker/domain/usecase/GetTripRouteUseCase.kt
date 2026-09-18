package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.domain.RoutePolylineReconstructor
import com.odys.mototriptracker.domain.TripRepository
import com.odys.mototriptracker.domain.WaypointRoadNameResolver
import com.odys.mototriptracker.domain.model.RoutePoint
import com.odys.mototriptracker.domain.model.Trip
import com.odys.mototriptracker.util.AppLogger
import javax.inject.Inject

data class TripRouteDetails(
    val trip: Trip,
    val routePoints: List<RoutePoint>,
    val waypoints: List<RoutePoint>,
    /** True when the trail was rebuilt from [Trip.encodedRoutePolyline]. */
    val usedPolylineFallback: Boolean = false,
)

class GetTripRouteUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val polylineReconstructor: RoutePolylineReconstructor,
    private val roadNameResolver: WaypointRoadNameResolver,
) {
    operator fun invoke(tripId: Long): TripRouteDetails? {
        val trip = tripRepository.getTrip(tripId) ?: return null
        var points = tripRepository.getRoutePointsForMap(tripId)
        var usedFallback = false

        // Summary can show a route from the encoded polyline even when the
        // ObjectBox point query returns nothing (seen after long background rides).
        if (points.size < 2) {
            val reconstructed = polylineReconstructor.reconstructPoints(
                encoded = trip.encodedRoutePolyline,
                startTimeMs = trip.startTime,
                endTimeMs = trip.endTime,
            )
            if (reconstructed.size >= 2) {
                points = reconstructed
                usedFallback = true
                AppLogger.w(
                    AppLogger.Category.PERSISTENCE,
                    "FullRoute fallback to encoded polyline id=$tripId verts=${reconstructed.size}",
                )
            }
        }

        val waypoints = if (usedFallback) {
            emptyList()
        } else {
            enrichStartEndRoadNames(tripRepository.getWaypointsForTrip(tripId))
        }

        return TripRouteDetails(
            trip = trip,
            routePoints = points,
            waypoints = waypoints,
            usedPolylineFallback = usedFallback,
        )
    }

    /**
     * Older rides often stored coordinate fallbacks when geocode ran on the main
     * thread or Google Geocoding was denied. Refresh Departure / Arrival labels.
     */
    private fun enrichStartEndRoadNames(
        waypoints: List<RoutePoint>,
    ): List<RoutePoint> {
        val updated = mutableListOf<RoutePoint>()
        val enriched = waypoints.map { waypoint ->
            val type = waypoint.waypointType
            if (type != "START" && type != "END") return@map waypoint
            val subtitle = waypoint.waypointSubtitle
            if (subtitle.isNotBlank() && !roadNameResolver.looksLikeCoordinates(subtitle)) {
                return@map waypoint
            }
            val road = roadNameResolver.resolveRoadName(
                waypoint.latitude,
                waypoint.longitude,
            )
            if (road.isBlank() || roadNameResolver.looksLikeCoordinates(road)) {
                return@map waypoint
            }
            val refreshed = waypoint.copy(waypointSubtitle = road)
            updated.add(refreshed)
            AppLogger.i(
                AppLogger.Category.WAYPOINT,
                "Enriched ${type} waypoint id=${waypoint.id} → $road",
            )
            refreshed
        }
        if (updated.isNotEmpty()) {
            tripRepository.updateWaypointSubtitles(updated)
        }
        return enriched
    }
}
