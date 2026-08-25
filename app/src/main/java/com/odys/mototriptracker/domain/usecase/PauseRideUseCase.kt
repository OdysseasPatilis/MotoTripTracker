package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.trip.TripServiceController
import com.odys.mototriptracker.domain.TripManager
import javax.inject.Inject

class PauseRideUseCase @Inject constructor(
    private val tripManager: TripManager,
    private val serviceController: TripServiceController
) {
    operator fun invoke() {
        tripManager.pauseTrip()
        serviceController.pauseService()
    }
}
