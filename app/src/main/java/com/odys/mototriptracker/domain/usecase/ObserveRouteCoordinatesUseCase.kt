package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.domain.TripManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveRouteCoordinatesUseCase @Inject constructor(
    private val tripManager: TripManager,
) {
    operator fun invoke(): StateFlow<List<RouteCoordinate>> = tripManager.routeCoordinates
}
