package com.odys.mototriptracker.domain

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity

data class RouteReplayFrame(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val altitude: Double,
    val elapsedSeconds: Double,
    val segmentIndex: Int
)

/** Time-based route replay from persisted route points (timestamps in ms). */
class RouteReplayEngine(
    private val points: List<RoutePointEntity>
) {
    val isValid: Boolean get() = points.size >= 2

    val durationSeconds: Double
        get() {
            if (points.size < 2) return 0.0
            val first = points.first().timestamp
            val last = points.last().timestamp
            return maxOf(0.0, (last - first) / 1000.0)
        }

    fun frame(atElapsedSeconds: Double): RouteReplayFrame? {
        if (points.size < 2) return null
        val firstTs = points.first().timestamp
        val clamped = atElapsedSeconds.coerceIn(0.0, durationSeconds)
        val targetTs = firstTs + (clamped * 1000).toLong()

        if (targetTs <= points.first().timestamp) {
            val p = points.first()
            return RouteReplayFrame(
                latitude = p.latitude,
                longitude = p.longitude,
                speedKmh = p.speedMps * 3.6,
                altitude = p.altitude,
                elapsedSeconds = clamped,
                segmentIndex = 0
            )
        }

        for (index in 0 until points.lastIndex) {
            val a = points[index]
            val b = points[index + 1]
            if (targetTs in a.timestamp..b.timestamp) {
                val span = (b.timestamp - a.timestamp).toDouble()
                val t = if (span > 0) (targetTs - a.timestamp) / span else 0.0
                return RouteReplayFrame(
                    latitude = a.latitude + (b.latitude - a.latitude) * t,
                    longitude = a.longitude + (b.longitude - a.longitude) * t,
                    speedKmh = (a.speedMps + (b.speedMps - a.speedMps) * t) * 3.6,
                    altitude = a.altitude + (b.altitude - a.altitude) * t,
                    elapsedSeconds = clamped,
                    segmentIndex = index
                )
            }
        }

        val last = points.last()
        return RouteReplayFrame(
            latitude = last.latitude,
            longitude = last.longitude,
            speedKmh = last.speedMps * 3.6,
            altitude = last.altitude,
            elapsedSeconds = clamped,
            segmentIndex = maxOf(0, points.size - 2)
        )
    }

    fun trailCoordinates(upTo: RouteReplayFrame): List<RouteCoordinate> {
        if (points.isEmpty()) return emptyList()
        val coords = points.take(upTo.segmentIndex + 1).map {
            RouteCoordinate(it.latitude, it.longitude)
        }
        return coords + RouteCoordinate(upTo.latitude, upTo.longitude)
    }
}
