package com.odys.mototriptracker.data.camera

import android.content.Context
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Bundled offline traffic-camera points for a geographic region. */
data class TrafficCameraRegionPack(
    val id: String,
    val name: String,
    val version: Int,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val cameras: List<TrafficCamera>,
) {
    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in south..north && longitude in west..east
}

@Singleton
class TrafficCameraRegionPackStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val context = context

    val packs: List<TrafficCameraRegionPack> by lazy {
        listOfNotNull(
            loadBundled("greece_traffic_cameras"),
            loadBundled("athens_traffic_cameras"),
        )
    }

    fun isInsideBundledRegion(latitude: Double, longitude: Double): Boolean =
        packs.any { it.contains(latitude, longitude) }

    private fun loadBundled(assetName: String): TrafficCameraRegionPack? {
        return try {
            val json = context.assets.open("$assetName.json").bufferedReader().use { it.readText() }
            decode(json).also { pack ->
                AppLogger.i(
                    AppLogger.Category.TRAFFIC_CAMERA,
                    "Loaded camera pack ${pack.id} v${pack.version} count=${pack.cameras.size}",
                )
            }
        } catch (t: Throwable) {
            AppLogger.e(AppLogger.Category.TRAFFIC_CAMERA, "Failed loading $assetName.json", t)
            null
        }
    }

    companion object {
        fun decode(json: String): TrafficCameraRegionPack {
            val root = JSONObject(json)
            val bbox = root.getJSONObject("bbox")
            val camerasJson = root.getJSONArray("cameras")
            val cameras = buildList {
                for (i in 0 until camerasJson.length()) {
                    val entry = camerasJson.getJSONObject(i)
                    val id = entry.optString("id")
                    val lat = entry.optDouble("lat", Double.NaN)
                    val lon = entry.optDouble("lon", Double.NaN)
                    if (id.isBlank() || !lat.isFinite() || !lon.isFinite()) continue
                    if (kotlin.math.abs(lat) > 90 || kotlin.math.abs(lon) > 180) continue
                    val kind = when (entry.optString("kind")) {
                        "speed" -> TrafficCameraKind.Speed
                        "redLight" -> TrafficCameraKind.RedLight
                        else -> continue
                    }
                    add(TrafficCamera(id = id, latitude = lat, longitude = lon, kind = kind))
                }
            }
            return TrafficCameraRegionPack(
                id = root.getString("id"),
                name = root.getString("name"),
                version = root.getInt("version"),
                south = bbox.getDouble("south"),
                west = bbox.getDouble("west"),
                north = bbox.getDouble("north"),
                east = bbox.getDouble("east"),
                cameras = cameras,
            )
        }

        fun encode(pack: TrafficCameraRegionPack): String {
            val cameras = JSONArray()
            pack.cameras.forEach { camera ->
                cameras.put(
                    JSONObject()
                        .put("id", camera.id)
                        .put("lat", camera.latitude)
                        .put("lon", camera.longitude)
                        .put(
                            "kind",
                            when (camera.kind) {
                                TrafficCameraKind.Speed -> "speed"
                                TrafficCameraKind.RedLight -> "redLight"
                            },
                        ),
                )
            }
            return JSONObject()
                .put("id", pack.id)
                .put("name", pack.name)
                .put("version", pack.version)
                .put(
                    "bbox",
                    JSONObject()
                        .put("south", pack.south)
                        .put("west", pack.west)
                        .put("north", pack.north)
                        .put("east", pack.east),
                )
                .put("cameras", cameras)
                .toString()
        }
    }
}
