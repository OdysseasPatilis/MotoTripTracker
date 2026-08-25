package com.odys.mototriptracker.ui.history

import com.odys.mototriptracker.data.trip.TripEntity

data class RideHistoryUiState(
    val rides: List<TripEntity> = emptyList(),
    val isLoading: Boolean = false
)
