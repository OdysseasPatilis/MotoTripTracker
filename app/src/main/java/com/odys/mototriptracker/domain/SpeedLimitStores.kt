package com.odys.mototriptracker.domain

/** Disk-backed speed-limit grid cache (offline reuse between rides). */
interface SpeedLimitCache {
    fun load(): MutableMap<String, Int>
    fun save(cache: Map<String, Int?>)
}

/** Bundled offline region packs (e.g. Greater Athens). */
data class RegionPackHit(
    val packId: String,
    val limitKmh: Int,
)

interface SpeedLimitRegionPacks {
    val packCount: Int
    fun isInsideBundledRegion(latitude: Double, longitude: Double): Boolean
    fun lookup(latitude: Double, longitude: Double): RegionPackHit?
}
