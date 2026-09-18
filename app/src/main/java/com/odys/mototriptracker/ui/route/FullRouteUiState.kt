package com.odys.mototriptracker.ui.route

import com.odys.mototriptracker.domain.model.RoutePoint
import com.odys.mototriptracker.domain.model.Trip

data class FullRouteUiState(
    val trip: Trip? = null,
    val ridePoints: List<RidePoint> = emptyList(),
    val routePoints: List<RoutePoint> = emptyList(),
    val waypoints: List<Waypoint> = emptyList(),
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    /** Trail rebuilt from encoded polyline; waypoints unavailable in this mode. */
    val usedPolylineFallback: Boolean = false,
)
