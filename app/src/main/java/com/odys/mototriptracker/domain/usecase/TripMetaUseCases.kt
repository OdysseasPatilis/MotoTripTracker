package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.trip.TripRepository
import javax.inject.Inject

class UpdateTripTitleUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(tripId: Long, title: String) {
        tripRepository.updateTripTitle(tripId, title)
    }
}

class ToggleFavoriteUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(tripId: Long): Boolean = tripRepository.toggleFavorite(tripId)
}
