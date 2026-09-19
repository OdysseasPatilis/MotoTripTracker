package com.odys.mototriptracker.domain

/** Offline traffic-camera pack download progress for the live map banner. */
sealed class TrafficCameraPackDownloadStatus {
    data object Idle : TrafficCameraPackDownloadStatus()
    data class Downloading(
        val countryCode: String,
        val countryName: String?,
    ) : TrafficCameraPackDownloadStatus()
    data class Failed(val message: String) : TrafficCameraPackDownloadStatus()
}
