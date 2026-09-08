package com.odys.mototriptracker.domain.usecase

import android.content.Context
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.RoutePolylineFallback
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.data.trip.TripRepository
import com.odys.mototriptracker.data.waypoint.WaypointReverseGeocoder
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class TripRouteDetails(
    val trip: TripEntity,
    val routePoints: List<RoutePointEntity>,
    val waypoints: List<RoutePointEntity>,
    /** True when the trail was rebuilt from [TripEntity.encodedRoutePolyline]. */
    val usedPolylineFallback: Boolean = false,
)

class GetTripRouteUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    @param:ApplicationContext private val context: Context,
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
        waypoints: List<RoutePointEntity>,
    ): List<RoutePointEntity> {
        val updated = mutableListOf<RoutePointEntity>()
        for (waypoint in waypoints) {
            val type = waypoint.waypointType
            if (type != "START" && type != "END") continue
            val subtitle = waypoint.waypointSubtitle
            if (subtitle.isNotBlank() && !WaypointReverseGeocoder.looksLikeCoordinates(subtitle)) {
                continue
            }
            val road = WaypointReverseGeocoder.resolveRoadName(
                context,
                waypoint.latitude,
                waypoint.longitude,
            )
            if (road.isBlank() || WaypointReverseGeocoder.looksLikeCoordinates(road)) continue
            waypoint.waypointSubtitle = road
            updated.add(waypoint)
            AppLogger.i(
                AppLogger.Category.WAYPOINT,
                "Enriched ${type} waypoint id=${waypoint.id} → $road"
            )
        }
        if (updated.isNotEmpty()) {
            tripRepository.updateWaypointSubtitles(updated)
        }
        return waypoints
    }
}
