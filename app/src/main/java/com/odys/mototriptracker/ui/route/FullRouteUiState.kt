package com.odys.mototriptracker.ui.route

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.ui.dashboard.RidePoint
import com.odys.mototriptracker.ui.dashboard.Waypoint

data class FullRouteUiState(
    val trip: TripEntity? = null,
    val ridePoints: List<RidePoint> = emptyList(),
    val routePointEntities: List<RoutePointEntity> = emptyList(),
    val waypoints: List<Waypoint> = emptyList(),
    val isLoading: Boolean = true,
    val notFound: Boolean = false
)
