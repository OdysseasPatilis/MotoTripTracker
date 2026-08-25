package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.domain.RideSessionState
import com.odys.mototriptracker.domain.TripManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveRideSessionUseCase @Inject constructor(
    private val tripManager: TripManager
) {
    operator fun invoke(): StateFlow<RideSessionState> = tripManager.sessionState
}
