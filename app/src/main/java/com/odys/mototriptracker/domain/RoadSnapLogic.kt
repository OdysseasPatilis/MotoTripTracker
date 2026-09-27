package com.odys.mototriptracker.domain

/** Pure buffer / throttle / freshness rules for Google Roads snapping. */
object RoadSnapLogic {
    const val MAX_BUFFER_POINTS = 12
    const val MIN_SNAP_MOVE_METERS = 25.0
    const val MIN_SNAP_INTERVAL_MS = 2_500L
    const val FRESH_SNAP_MS = 5_000L

    data class GpsPoint(
        val latitude: Double,
        val longitude: Double,
        val timeMs: Long,
    )

    fun appendPoint(
        buffer: List<GpsPoint>,
        latitude: Double,
        longitude: Double,
        timeMs: Long,
    ): List<GpsPoint> {
        val next = buffer + GpsPoint(latitude, longitude, timeMs)
        return if (next.size <= MAX_BUFFER_POINTS) next else next.takeLast(MAX_BUFFER_POINTS)
    }

    fun shouldSnap(
        buffer: List<GpsPoint>,
        lastSnapLat: Double?,
        lastSnapLng: Double?,
        lastSnapRequestMs: Long,
        nowMs: Long,
    ): Boolean {
        if (buffer.isEmpty()) return false
        if (lastSnapLat == null || lastSnapLng == null) return true
        val latest = buffer.last()
        val moved = Geo.distanceMeters(
            lastSnapLat, lastSnapLng, latest.latitude, latest.longitude,
        ) >= MIN_SNAP_MOVE_METERS
        val waited = nowMs - lastSnapRequestMs >= MIN_SNAP_INTERVAL_MS
        return moved || waited
    }

    fun isFresh(snap: SnappedRoad?, nowMs: Long, maxAgeMs: Long = FRESH_SNAP_MS): Boolean {
        if (snap == null) return false
        return nowMs - snap.snappedAtMs <= maxAgeMs
    }

    /** Prefer the snapped point that corresponds to the newest raw sample. */
    fun pickLatestSnap(
        snappedPoints: List<Pair<Double, Double>>,
        placeIds: List<String?>,
        originalIndices: List<Int?>,
        rawPath: List<GpsPoint>,
        nowMs: Long,
    ): SnappedRoad? {
        if (snappedPoints.isEmpty() || rawPath.isEmpty()) return null
        val lastRawIndex = rawPath.lastIndex
        var bestIdx = snappedPoints.lastIndex
        for (i in snappedPoints.indices) {
            val orig = originalIndices.getOrNull(i)
            if (orig == lastRawIndex) {
                bestIdx = i
                break
            }
            if (orig != null && orig <= lastRawIndex) bestIdx = i
        }
        val (lat, lng) = snappedPoints[bestIdx]
        val raw = rawPath.last()
        return SnappedRoad(
            latitude = lat,
            longitude = lng,
            placeId = placeIds.getOrNull(bestIdx),
            rawLatitude = raw.latitude,
            rawLongitude = raw.longitude,
            snappedAtMs = nowMs,
        )
    }
}
