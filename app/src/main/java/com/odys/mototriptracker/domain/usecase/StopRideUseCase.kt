package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.trip.TripServiceController
import com.odys.mototriptracker.domain.TripManager
import javax.inject.Inject

data class StopRideResult(
    val saved: Boolean,
    val distanceMeters: Float
)

class StopRideUseCase @Inject constructor(
    private val tripManager: TripManager,
    private val serviceController: TripServiceController
) {
    operator fun invoke(minDistanceMeters: Float = MIN_DISTANCE_METERS): StopRideResult {
        val distanceMeters = tripManager.tripStats.value.distanceMeters
        val saved = tripManager.stopTrip(minDistanceMeters)
        serviceController.stopService()
        return StopRideResult(saved = saved, distanceMeters = distanceMeters)
    }

    companion object {
        const val MIN_DISTANCE_METERS = TripManager.MIN_SAVE_DISTANCE_METERS
    }
}
