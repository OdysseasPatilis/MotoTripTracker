package com.odys.mototriptracker.domain

import kotlin.math.truncate

/** Pure helpers for speed-limit cache keys, throttle, and pack/cache plausibility. */
object SpeedLimitLogic {
    const val MIN_MOVE_METERS = 35.0
    const val MIN_INTERVAL_MS = 15_000L
    const val GRID_SCALE = 500.0

    /**
     * True when GPS speed is not clearly above the posted limit
     * (pack/cache may be a side street while riding a highway).
     */
    fun limitLooksPlausible(kmh: Int, speedMps: Float): Boolean {
        if (speedMps < 0f) return true
        return speedMps * 3.6f <= kmh + 25f
    }

    fun gridKey(latitude: Double, longitude: Double): String {
        val latCell = truncate(latitude * GRID_SCALE).toLong()
        val lngCell = truncate(longitude * GRID_SCALE).toLong()
        return "${latCell}_${lngCell}"
    }

    /** Keys for the 8 neighbouring cells around [latitude]/[longitude] (excludes self). */
    fun neighbourGridKeys(latitude: Double, longitude: Double): List<String> {
        val latCell = truncate(latitude * GRID_SCALE).toLong()
        val lngCell = truncate(longitude * GRID_SCALE).toLong()
        val keys = ArrayList<String>(8)
        for (dLat in -1..1) {
            for (dLng in -1..1) {
                if (dLat == 0 && dLng == 0) continue
                keys += "${latCell + dLat}_${lngCell + dLng}"
            }
        }
        return keys
    }

    fun shouldQuery(
        latitude: Double,
        longitude: Double,
        lastLat: Double?,
        lastLng: Double?,
        lastQueryTimeMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (lastLat == null || lastLng == null) return true
        val movedEnough = Geo.distanceMeters(lastLat, lastLng, latitude, longitude) >= MIN_MOVE_METERS
        val waitedEnough = nowMs - lastQueryTimeMs >= MIN_INTERVAL_MS
        return movedEnough || waitedEnough
    }
}
