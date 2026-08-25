package com.odys.mototriptracker.ui.tracker

import com.odys.mototriptracker.domain.TripStats

data class RideTrackerUiState(
    val stats: TripStats = TripStats(),
    val isTracking: Boolean = false,
    val isPaused: Boolean = false
)
