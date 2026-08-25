package com.odys.mototriptracker.ui.summary

import com.odys.mototriptracker.data.trip.TripEntity

data class RideSummaryUiState(
    val trip: TripEntity? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val notFound: Boolean = false
)
