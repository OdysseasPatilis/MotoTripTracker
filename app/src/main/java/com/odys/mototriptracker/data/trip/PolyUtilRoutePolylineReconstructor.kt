package com.odys.mototriptracker.data.trip

import com.odys.mototriptracker.domain.RoutePolylineReconstructor
import com.odys.mototriptracker.domain.model.RoutePoint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolyUtilRoutePolylineReconstructor @Inject constructor() : RoutePolylineReconstructor {
    override fun reconstructPoints(
        encoded: String?,
        startTimeMs: Long,
        endTimeMs: Long,
    ): List<RoutePoint> = RoutePolylineFallback.reconstructPoints(encoded, startTimeMs, endTimeMs)
}
