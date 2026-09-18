package com.odys.mototriptracker.domain

import com.odys.mototriptracker.domain.model.RoutePoint

/** Resolves a human-readable road name for a coordinate (waypoint labels). */
interface WaypointRoadNameResolver {
    fun looksLikeCoordinates(label: String): Boolean
    fun resolveRoadName(latitude: Double, longitude: Double): String
}

/** Rebuilds route points from an encoded polyline when ObjectBox rows are missing. */
interface RoutePolylineReconstructor {
    fun reconstructPoints(
        encoded: String?,
        startTimeMs: Long,
        endTimeMs: Long,
    ): List<RoutePoint>
}
