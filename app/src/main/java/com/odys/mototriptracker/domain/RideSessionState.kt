package com.odys.mototriptracker.domain

data class RideSessionState(
    val stats: TripStats = TripStats(),
    val isActive: Boolean = false,
    val isPaused: Boolean = false
)
