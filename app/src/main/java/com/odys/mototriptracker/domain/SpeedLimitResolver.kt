package com.odys.mototriptracker.domain

import com.odys.mototriptracker.data.road.SpeedLimitCacheStore
import com.odys.mototriptracker.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class SpeedLimitResolver @Inject constructor(
    private val speedLimitProvider: SpeedLimitProvider,
    private val tripManager: TripManager,
    private val cacheStore: SpeedLimitCacheStore
) {
    private val cache: MutableMap<String, Int?> = mutableMapOf<String, Int?>().apply {
        putAll(cacheStore.load())
    }

    private var lastQueryLat: Double? = null
    private var lastQueryLng: Double? = null
    private var lastQueryTimeMs: Long = 0L
    private var lookupJob: Job? = null
    private var dirty = false

    init {
        AppLogger.i(AppLogger.Category.SPEED_LIMIT, "Loaded ${cache.size} cached speed-limit cells")
    }

    fun reset() {
        lookupJob?.cancel()
        lookupJob = null
        // Keep disk cache — only clear in-memory "none" misses for this ride.
        cache.keys.filter { cache[it] == null }.forEach { cache.remove(it) }
        lastQueryLat = null
        lastQueryLng = null
        lastQueryTimeMs = 0L
        persistIfNeeded()
        AppLogger.d(AppLogger.Category.SPEED_LIMIT, "Resolver reset (kept ${cache.size} offline cells)")
    }

    fun onLocationUpdate(latitude: Double, longitude: Double, scope: CoroutineScope) {
        if (!shouldQuery(latitude, longitude)) return

        val cacheKey = gridKey(latitude, longitude)
        if (cacheKey in cache) {
            val cached = cache[cacheKey]
            cached?.let { tripManager.updateRoadSpeedLimit(it) }
            lastQueryLat = latitude
            lastQueryLng = longitude
            lastQueryTimeMs = System.currentTimeMillis()
            AppLogger.d(
                AppLogger.Category.SPEED_LIMIT,
                "Cache hit key=$cacheKey limit=${cached ?: "none"} @ ${AppLogger.coordinate(latitude, longitude)}"
            )
            return
        }

        // Soft offline fallback: nearest neighbouring cell with a known limit.
        nearestCachedLimit(latitude, longitude)?.let { nearby ->
            tripManager.updateRoadSpeedLimit(nearby)
            AppLogger.d(
                AppLogger.Category.SPEED_LIMIT,
                "Offline neighbour limit=$nearby @ ${AppLogger.coordinate(latitude, longitude)}"
            )
        }

        lookupJob?.cancel()
        lookupJob = scope.launch {
            AppLogger.d(
                AppLogger.Category.SPEED_LIMIT,
                "Lookup start @ ${AppLogger.coordinate(latitude, longitude)}"
            )
            val limit = try {
                speedLimitProvider.getSpeedLimitKmh(latitude, longitude)
            } catch (t: Throwable) {
                AppLogger.e(AppLogger.Category.SPEED_LIMIT, "Lookup failed", t)
                null
            }
            cache[cacheKey] = limit
            dirty = true
            lastQueryLat = latitude
            lastQueryLng = longitude
            lastQueryTimeMs = System.currentTimeMillis()
            if (limit != null) {
                tripManager.updateRoadSpeedLimit(limit)
                persistIfNeeded()
                AppLogger.i(
                    AppLogger.Category.SPEED_LIMIT,
                    "Lookup ok → $limit km/h @ ${AppLogger.coordinate(latitude, longitude)}"
                )
            } else {
                AppLogger.w(
                    AppLogger.Category.SPEED_LIMIT,
                    "No maxspeed @ ${AppLogger.coordinate(latitude, longitude)}"
                )
            }
        }
    }

    private fun nearestCachedLimit(latitude: Double, longitude: Double): Int? {
        val latCell = (latitude * GRID_SCALE).toLong()
        val lngCell = (longitude * GRID_SCALE).toLong()
        for (dLat in -1..1) {
            for (dLng in -1..1) {
                if (dLat == 0 && dLng == 0) continue
                val key = "${latCell + dLat}_${lngCell + dLng}"
                cache[key]?.let { return it }
            }
        }
        return null
    }

    private fun persistIfNeeded() {
        if (!dirty) return
        cacheStore.save(cache)
        dirty = false
    }

    private fun shouldQuery(latitude: Double, longitude: Double): Boolean {
        val now = System.currentTimeMillis()
        val lastLat = lastQueryLat
        val lastLng = lastQueryLng
        if (lastLat == null || lastLng == null) return true

        val movedEnough = haversineMeters(lastLat, lastLng, latitude, longitude) >= MIN_MOVE_METERS
        val waitedEnough = now - lastQueryTimeMs >= MIN_INTERVAL_MS
        return movedEnough || waitedEnough
    }

    private fun gridKey(latitude: Double, longitude: Double): String {
        val latCell = (latitude * GRID_SCALE).toLong()
        val lngCell = (longitude * GRID_SCALE).toLong()
        return "${latCell}_$lngCell"
    }

    private fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return earthRadiusM * 2 * asin(sqrt(a))
    }

    companion object {
        private const val MIN_MOVE_METERS = 35.0
        private const val MIN_INTERVAL_MS = 15_000L
        private const val GRID_SCALE = 500.0
    }
}
