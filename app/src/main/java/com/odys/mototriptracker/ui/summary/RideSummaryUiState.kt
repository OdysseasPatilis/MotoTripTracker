package com.odys.mototriptracker.ui.summary

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.domain.RideMoments

data class RideSummaryUiState(
    val trip: TripEntity? = null,
    val routePoints: List<RoutePointEntity> = emptyList(),
    val moments: RideMoments = RideMoments(emptyList()),
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val notFound: Boolean = false
)
