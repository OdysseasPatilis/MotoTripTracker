package com.odys.mototriptracker.ui.tracker

import com.odys.mototriptracker.data.navigation.NavigationState
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.domain.TripStats

data class RideTrackerUiState(
    val stats: TripStats = TripStats(),
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val routeCoordinates: List<RouteCoordinate> = emptyList(),
    val navigation: NavigationState = NavigationState(),
    val discardBanner: String? = null,
    val showDestinationSearch: Boolean = false,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastBearing: Float = 0f,
    val lastSpeedMps: Float = 0f
)
