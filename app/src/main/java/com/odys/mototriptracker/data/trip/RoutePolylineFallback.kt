package com.odys.mototriptracker.data.trip

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity

/**
 * Rebuilds display/replay points from [TripEntity.encodedRoutePolyline] when the
 * ObjectBox point query returns fewer than 2 rows — mirrors iOS FullRoute fallback
 * (seen after long background rides).
 */
object RoutePolylineFallback {

    fun reconstructPoints(
        encoded: String?,
        startTimeMs: Long,
        endTimeMs: Long,
    ): List<RoutePointEntity> {
        if (encoded.isNullOrBlank()) return emptyList()
        val decoded = runCatching { PolyUtil.decode(encoded) }.getOrNull().orEmpty()
        if (decoded.size < 2) return emptyList()
        return reconstructFromLatLngs(decoded, startTimeMs, endTimeMs)
    }

    /** Pure helper for tests — interpolated timestamps across [startTimeMs, endTimeMs]. */
    fun reconstructFromLatLngs(
        decoded: List<LatLng>,
        startTimeMs: Long,
        endTimeMs: Long,
    ): List<RoutePointEntity> {
        if (decoded.size < 2) return emptyList()
        val start = startTimeMs
        val end = if (endTimeMs > start) endTimeMs else start + (decoded.size - 1) * 1000L
        val span = maxOf(end - start, (decoded.size - 1).toLong())
        return decoded.mapIndexed { index, latLng ->
            val t = if (decoded.size == 1) {
                start
            } else {
                start + span * index / (decoded.size - 1)
            }
            RoutePointEntity(
                latitude = latLng.latitude,
                longitude = latLng.longitude,
                altitude = 0.0,
                speedMps = 0f,
                timestamp = t,
            )
        }
    }
}
