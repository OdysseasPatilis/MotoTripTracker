package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.trip.TripServiceController
import com.odys.mototriptracker.domain.TripManager
import javax.inject.Inject

data class StopRideResult(
    val distanceMeters: Float,
    val isTooShort: Boolean
)

class StopRideUseCase @Inject constructor(
    private val tripManager: TripManager,
    private val serviceController: TripServiceController
) {
    operator fun invoke(minDistanceMeters: Float = MIN_DISTANCE_METERS): StopRideResult {
        tripManager.stopTrip()
        serviceController.stopService()

        val distanceMeters = tripManager.tripStats.value.distanceMeters
        return StopRideResult(
            distanceMeters = distanceMeters,
            isTooShort = distanceMeters < minDistanceMeters
        )
    }

    companion object {
        const val MIN_DISTANCE_METERS = 50f
    }
}
