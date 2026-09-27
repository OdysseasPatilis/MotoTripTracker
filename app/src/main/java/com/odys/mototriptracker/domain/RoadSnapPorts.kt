package com.odys.mototriptracker.domain

import kotlinx.coroutines.flow.StateFlow

/** Live Google Roads map-match for speed limits and navigation origin. */
interface RoadSnapper {
    val snapped: StateFlow<SnappedRoad?>
    fun onRawLocation(latitude: Double, longitude: Double, nowMs: Long = System.currentTimeMillis())
    fun latestFresh(nowMs: Long = System.currentTimeMillis()): SnappedRoad?
    fun reset()
}

/** Optional Roads speedLimits (Asset Tracking). */
sealed class PostedSpeedLimitLookup {
    data class Value(val kmh: Int) : PostedSpeedLimitLookup()
    data object Missing : PostedSpeedLimitLookup()
    data object Unavailable : PostedSpeedLimitLookup()
    data object Failed : PostedSpeedLimitLookup()
}

interface PostedSpeedLimitSource {
    val isDisabled: Boolean
    suspend fun lookupPlaceId(placeId: String): PostedSpeedLimitLookup
}
