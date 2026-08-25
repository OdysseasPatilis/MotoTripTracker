package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.data.trip.TripRepository
import javax.inject.Inject

data class TripRouteDetails(
    val trip: TripEntity,
    val routePoints: List<RoutePointEntity>,
    val waypoints: List<RoutePointEntity>
)

class GetTripRouteUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(tripId: Long): TripRouteDetails? {
        val trip = tripRepository.getTrip(tripId) ?: return null
        return TripRouteDetails(
            trip = trip,
            routePoints = tripRepository.getRoutePointsForMap(tripId),
            waypoints = tripRepository.getWaypointsForTrip(tripId)
        )
    }
}
