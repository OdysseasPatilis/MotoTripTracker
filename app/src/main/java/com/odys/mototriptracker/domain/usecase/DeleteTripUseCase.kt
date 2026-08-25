package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.trip.TripRepository
import javax.inject.Inject

class DeleteTripUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(tripId: Long) {
        tripRepository.deleteTrip(tripId)
    }
}
