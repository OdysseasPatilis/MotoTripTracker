package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.domain.GpxExporter
import com.odys.mototriptracker.domain.model.RoutePoint
import com.odys.mototriptracker.domain.model.Trip
import javax.inject.Inject

class ExportGpxUseCase @Inject constructor() {
    operator fun invoke(trip: Trip, points: List<RoutePoint>): String =
        GpxExporter.build(trip, points)
}
