package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.domain.TripRepository
import com.odys.mototriptracker.domain.model.Trip
import javax.inject.Inject

class GetTripHistoryUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(): List<Trip> = tripRepository.getTrips()
}
