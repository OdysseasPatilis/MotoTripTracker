package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.data.trip.TripRepository
import javax.inject.Inject

class GetTripHistoryUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(): List<TripEntity> = tripRepository.getTrips().reversed()
}
