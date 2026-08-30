package com.odys.mototriptracker.data.road

import android.content.Context
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.truncate

/** Bundled offline speed-limit grid for a geographic region (e.g. Greater Athens). */
data class SpeedLimitRegionPack(
    val id: String,
    val name: String,
    val version: Int,
    val gridScale: Double,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val cells: Map<String, Int>
) {
    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in south..north && longitude in west..east

    /** Exact grid cell, then 8-neighbour fallback within this pack. */
    fun limit(latitude: Double, longitude: Double): Int? {
        if (!contains(latitude, longitude)) return null

        val latCell = truncate(latitude * gridScale).toLong()
        val lngCell = truncate(longitude * gridScale).toLong()
        val key = "${latCell}_$lngCell"
        cells[key]?.let { return it }

        for (dLat in -1..1) {
            for (dLng in -1..1) {
                if (dLat == 0 && dLng == 0) continue
                cells["${latCell + dLat}_${lngCell + dLng}"]?.let { return it }
            }
        }
        return null
    }
}

@Singleton
class SpeedLimitRegionPackStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val context = context
    val packs: List<SpeedLimitRegionPack> by lazy {
        listOfNotNull(loadBundled("athens_speed_limits"))
    }

    fun isInsideBundledRegion(latitude: Double, longitude: Double): Boolean =
        packs.any { it.contains(latitude, longitude) }

    fun limit(latitude: Double, longitude: Double): Pair<SpeedLimitRegionPack, Int>? {
        for (pack in packs) {
            if (!pack.contains(latitude, longitude)) continue
            val kmh = pack.limit(latitude, longitude) ?: continue
            return pack to kmh
        }
        return null
    }

    private fun loadBundled(assetName: String): SpeedLimitRegionPack? {
        return try {
            val json = context.assets.open("$assetName.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val bbox = root.getJSONObject("bbox")
            val cellsJson = root.getJSONObject("cells")
            val cells = mutableMapOf<String, Int>()
            val keys = cellsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = cellsJson.getInt(key)
                if (value in 5..200) cells[key] = value
            }
            val pack = SpeedLimitRegionPack(
                id = root.getString("id"),
                name = root.getString("name"),
                version = root.getInt("version"),
                gridScale = root.getDouble("gridScale"),
                south = bbox.getDouble("south"),
                west = bbox.getDouble("west"),
                north = bbox.getDouble("north"),
                east = bbox.getDouble("east"),
                cells = cells
            )
            AppLogger.i(
                AppLogger.Category.SPEED_LIMIT,
                "Loaded region pack ${pack.id} v${pack.version} cells=${pack.cells.size}"
            )
            pack
        } catch (t: Throwable) {
            AppLogger.e(AppLogger.Category.SPEED_LIMIT, "Failed loading $assetName.json", t)
            null
        }
    }
}
