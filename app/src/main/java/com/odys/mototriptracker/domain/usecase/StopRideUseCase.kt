package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.trip.TripServiceController
import com.odys.mototriptracker.domain.TripManager
import javax.inject.Inject

data class StopRideResult(
    val saved: Boolean,
    val distanceMeters: Float,
    val tripId: Long? = null,
)

class StopRideUseCase @Inject constructor(
    private val tripManager: TripManager,
    private val serviceController: TripServiceController,
    private val uploadTripToCloudUseCase: UploadTripToCloudUseCase,
) {
    operator fun invoke(minDistanceMeters: Float = MIN_DISTANCE_METERS): StopRideResult {
        val distanceMeters = tripManager.tripStats.value.distanceMeters
        val result = tripManager.stopTrip(minDistanceMeters)
        serviceController.stopService()
        if (result.saved && result.tripId != null) {
            uploadTripToCloudUseCase(result.tripId)
        }
        return StopRideResult(
            saved = result.saved,
            distanceMeters = distanceMeters,
            tripId = result.tripId,
        )
    }

    companion object {
        const val MIN_DISTANCE_METERS = TripManager.MIN_SAVE_DISTANCE_METERS
    }
}
