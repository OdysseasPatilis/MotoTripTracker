package com.odys.mototriptracker.domain

import com.odys.mototriptracker.data.road.SpeedLimitCacheStore
import com.odys.mototriptracker.data.road.SpeedLimitRegionPackStore
import com.odys.mototriptracker.util.AppLogger
import com.odys.mototriptracker.util.LogThrottle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedLimitResolver @Inject constructor(
    private val speedLimitProvider: SpeedLimitProvider,
    private val tripManager: TripManager,
    private val cacheStore: SpeedLimitCacheStore,
    private val regionPackStore: SpeedLimitRegionPackStore
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
        AppLogger.i(
            AppLogger.Category.SPEED_LIMIT,
            "Loaded ${cache.size} cached speed-limit cells; ${regionPackStore.packs.size} region pack(s)"
        )
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

    /**
     * @param speedMps GPS speed used to detect implausible pack/cache hits
     * (e.g. residential 50 while riding at highway speed) and fall through to Overpass.
     */
    fun onLocationUpdate(
        latitude: Double,
        longitude: Double,
        speedMps: Float,
        scope: CoroutineScope
    ) {
        // Bundled city packs first (offline). Empty cells and implausible hits fall through.
        if (regionPackStore.isInsideBundledRegion(latitude, longitude)) {
            val hit = regionPackStore.limit(latitude, longitude)
            if (hit != null) {
                val (pack, kmh) = hit
                tripManager.updateRoadSpeedLimit(kmh)
                if (LogThrottle.shouldLog("speedLimit.pack.${pack.id}", 20_000L)) {
                    AppLogger.d(
                        AppLogger.Category.SPEED_LIMIT,
                        "Region pack ${pack.id} → $kmh km/h"
                    )
                }
                if (SpeedLimitLogic.limitLooksPlausible(kmh, speedMps)) {
                    lastQueryLat = latitude
                    lastQueryLng = longitude
                    lastQueryTimeMs = System.currentTimeMillis()
                    return
                }
                if (LogThrottle.shouldLog("speedLimit.packMismatch", 20_000L)) {
                    AppLogger.i(
                        AppLogger.Category.SPEED_LIMIT,
                        "Pack $kmh km/h looks low vs GPS — querying Overpass"
                    )
                }
            } else if (LogThrottle.shouldLog("speedLimit.pack.miss", 30_000L)) {
                AppLogger.d(
                    AppLogger.Category.SPEED_LIMIT,
                    "Inside region pack with no cell @ ${AppLogger.coordinate(latitude, longitude)}"
                )
            }
            // Miss or implausible → Overpass below.
        }

        if (!SpeedLimitLogic.shouldQuery(
                latitude,
                longitude,
                lastQueryLat,
                lastQueryLng,
                lastQueryTimeMs,
            )
        ) return

        val cacheKey = SpeedLimitLogic.gridKey(latitude, longitude)
        if (cacheKey in cache) {
            val cached = cache[cacheKey]
            if (cached != null && SpeedLimitLogic.limitLooksPlausible(cached, speedMps)) {
                tripManager.updateRoadSpeedLimit(cached)
                lastQueryLat = latitude
                lastQueryLng = longitude
                lastQueryTimeMs = System.currentTimeMillis()
                AppLogger.d(
                    AppLogger.Category.SPEED_LIMIT,
                    "Cache hit key=$cacheKey limit=$cached @ ${AppLogger.coordinate(latitude, longitude)}"
                )
                return
            }
        }

        // Soft offline fallback: nearest neighbouring cell with a known limit.
        nearestCachedLimit(latitude, longitude)
            ?.takeIf { SpeedLimitLogic.limitLooksPlausible(it, speedMps) }
            ?.let { nearby ->
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
        for (key in SpeedLimitLogic.neighbourGridKeys(latitude, longitude)) {
            cache[key]?.let { return it }
        }
        return null
    }

    private fun persistIfNeeded() {
        if (!dirty) return
        cacheStore.save(cache)
        dirty = false
    }
}
