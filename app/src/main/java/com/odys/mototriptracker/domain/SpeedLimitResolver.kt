package com.odys.mototriptracker.domain

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
    private val cacheStore: SpeedLimitCache,
    private val regionPackStore: SpeedLimitRegionPacks,
    private val roadSnapper: RoadSnapper,
    private val postedSpeedLimits: PostedSpeedLimitSource,
) {
    private val cache: MutableMap<String, Int?> = mutableMapOf<String, Int?>().apply {
        putAll(cacheStore.load())
    }

    private var lastQueryLat: Double? = null
    private var lastQueryLng: Double? = null
    private var lastQueryTimeMs: Long = 0L
    private var lookupJob: Job? = null
    private var dirty = false
    private var holdState = SpeedLimitHoldLogic.HoldState()

    init {
        AppLogger.i(
            AppLogger.Category.SPEED_LIMIT,
            "Loaded ${cache.size} cached speed-limit cells; ${regionPackStore.packCount} region pack(s)"
        )
    }

    fun reset() {
        lookupJob?.cancel()
        lookupJob = null
        cache.keys.filter { cache[it] == null }.forEach { cache.remove(it) }
        lastQueryLat = null
        lastQueryLng = null
        lastQueryTimeMs = 0L
        holdState = SpeedLimitHoldLogic.HoldState()
        roadSnapper.reset()
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
        val nowMs = System.currentTimeMillis()
        roadSnapper.onRawLocation(latitude, longitude, nowMs)

        val snap = roadSnapper.latestFresh(nowMs)
        val queryLat = snap?.latitude ?: latitude
        val queryLng = snap?.longitude ?: longitude
        val placeId = snap?.placeId

        if (!SpeedLimitLogic.shouldQuery(
                queryLat,
                queryLng,
                lastQueryLat,
                lastQueryLng,
                lastQueryTimeMs,
                nowMs,
            )
        ) {
            return
        }

        // Optional Google Roads speedLimits (Asset Tracking). Soft-fail if unlicensed.
        if (placeId != null && !postedSpeedLimits.isDisabled) {
            val placeKey = placeIdCacheKey(placeId)
            if (placeKey in cache) {
                val cached = cache[placeKey]
                if (cached != null && SpeedLimitLogic.limitLooksPlausible(cached, speedMps)) {
                    lastQueryLat = queryLat
                    lastQueryLng = queryLng
                    lastQueryTimeMs = nowMs
                    publishCandidate(cached, speedMps, nowMs)
                    return
                }
            }
        }

        // Bundled city packs (offline) — throttle + plausibility before UI.
        if (regionPackStore.isInsideBundledRegion(queryLat, queryLng)) {
            val hit = regionPackStore.lookup(queryLat, queryLng)
            if (hit != null) {
                val kmh = hit.limitKmh
                if (SpeedLimitLogic.limitLooksPlausible(kmh, speedMps)) {
                    lastQueryLat = queryLat
                    lastQueryLng = queryLng
                    lastQueryTimeMs = nowMs
                    if (LogThrottle.shouldLog("speedLimit.pack.${hit.packId}", 20_000L)) {
                        AppLogger.d(
                            AppLogger.Category.SPEED_LIMIT,
                            "Region pack ${hit.packId} → $kmh km/h (snapped=${snap != null})"
                        )
                    }
                    publishCandidate(kmh, speedMps, nowMs)
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
                    "Inside region pack with no cell @ ${AppLogger.coordinate(queryLat, queryLng)}"
                )
            }
        }

        val cacheKey = placeId?.let { placeIdCacheKey(it) }
            ?: SpeedLimitLogic.gridKey(queryLat, queryLng)
        if (cacheKey in cache) {
            val cached = cache[cacheKey]
            if (cached != null && SpeedLimitLogic.limitLooksPlausible(cached, speedMps)) {
                lastQueryLat = queryLat
                lastQueryLng = queryLng
                lastQueryTimeMs = nowMs
                publishCandidate(cached, speedMps, nowMs)
                AppLogger.d(
                    AppLogger.Category.SPEED_LIMIT,
                    "Cache hit key=$cacheKey limit=$cached @ ${AppLogger.coordinate(queryLat, queryLng)}"
                )
                return
            }
        }

        nearestCachedLimit(queryLat, queryLng)
            ?.takeIf { SpeedLimitLogic.limitLooksPlausible(it, speedMps) }
            ?.let { nearby ->
                publishCandidate(nearby, speedMps, nowMs)
                AppLogger.d(
                    AppLogger.Category.SPEED_LIMIT,
                    "Offline neighbour limit=$nearby @ ${AppLogger.coordinate(queryLat, queryLng)}"
                )
            }

        lookupJob?.cancel()
        lookupJob = scope.launch {
            AppLogger.d(
                AppLogger.Category.SPEED_LIMIT,
                "Lookup start @ ${AppLogger.coordinate(queryLat, queryLng)} placeId=$placeId"
            )

            var limit: Int? = null
            if (placeId != null && !postedSpeedLimits.isDisabled) {
                when (val roads = postedSpeedLimits.lookupPlaceId(placeId)) {
                    is PostedSpeedLimitLookup.Value -> limit = roads.kmh
                    PostedSpeedLimitLookup.Unavailable,
                    PostedSpeedLimitLookup.Missing,
                    PostedSpeedLimitLookup.Failed,
                    -> Unit
                }
            }
            if (limit == null) {
                limit = try {
                    speedLimitProvider.getSpeedLimitKmh(queryLat, queryLng)
                } catch (t: Throwable) {
                    AppLogger.e(AppLogger.Category.SPEED_LIMIT, "Lookup failed", t)
                    null
                }
            }

            cache[cacheKey] = limit
            dirty = true
            lastQueryLat = queryLat
            lastQueryLng = queryLng
            lastQueryTimeMs = System.currentTimeMillis()
            if (limit != null) {
                publishCandidate(limit, speedMps, System.currentTimeMillis())
                persistIfNeeded()
                AppLogger.i(
                    AppLogger.Category.SPEED_LIMIT,
                    "Lookup ok → $limit km/h @ ${AppLogger.coordinate(queryLat, queryLng)}"
                )
            } else {
                AppLogger.w(
                    AppLogger.Category.SPEED_LIMIT,
                    "No maxspeed @ ${AppLogger.coordinate(queryLat, queryLng)}"
                )
            }
        }
    }

    private fun publishCandidate(kmh: Int, speedMps: Float, nowMs: Long) {
        val decision = SpeedLimitHoldLogic.onCandidate(holdState, kmh, speedMps, nowMs)
        holdState = decision.state
        decision.publishKmh?.let { tripManager.updateRoadSpeedLimit(it) }
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

    companion object {
        fun placeIdCacheKey(placeId: String): String = "pid:$placeId"
    }
}
