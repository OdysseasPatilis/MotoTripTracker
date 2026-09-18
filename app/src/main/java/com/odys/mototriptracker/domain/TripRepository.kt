package com.odys.mototriptracker.domain

import com.odys.mototriptracker.domain.model.RoutePoint
import com.odys.mototriptracker.domain.model.Trip

/**
 * Trip persistence port. Implementations live in the data layer (ObjectBox today).
 */
interface TripRepository {
    fun startNewTrip(startTimeMs: Long): Long

    fun addRoutePointAndUpdateStats(
        tripId: Long,
        lat: Double,
        lng: Double,
        alt: Double,
        speedMps: Float,
        timeMs: Long,
        runningStats: TripStats,
    )

    fun saveTrip(tripId: Long, finalStats: TripStats)

    fun updateWaypointSubtitles(points: List<RoutePoint>)

    fun getTrips(): List<Trip>

    fun getTrip(id: Long): Trip?

    fun updateTripTitle(id: Long, title: String)

    fun toggleFavorite(id: Long): Boolean

    fun deleteTrip(id: Long)

    fun getWaypointsForTrip(tripId: Long): List<RoutePoint>

    fun getRoutePointsForMap(tripId: Long): List<RoutePoint>
}
