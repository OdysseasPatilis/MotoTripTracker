package com.odys.mototriptracker.ui.summary

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.domain.RideMoments

sealed interface CloudUploadStatus {
    data object Idle : CloudUploadStatus
    data object Uploading : CloudUploadStatus
    data object Success : CloudUploadStatus
    data class Error(val message: String) : CloudUploadStatus
}

data class RideSummaryUiState(
    val trip: TripEntity? = null,
    val routePoints: List<RoutePointEntity> = emptyList(),
    val moments: RideMoments = RideMoments(emptyList()),
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val notFound: Boolean = false,
    val backendEnabled: Boolean = false,
    val uploadStatus: CloudUploadStatus = CloudUploadStatus.Idle,
)
