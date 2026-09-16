package com.odys.mototriptracker.data.navigation

import com.google.maps.android.PolyUtil
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.util.AppLogger
import com.odys.mototriptracker.util.MapsApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class DirectionsResult(
    val distanceMeters: Double,
    val carTravelTimeSeconds: Double,
    val motoTravelTimeSeconds: Double,
    val trafficDelaySeconds: Double = 0.0,
    val coordinates: List<RouteCoordinate>,
    val steps: List<NavStep>,
)

/**
 * Google Directions (+ traffic) with OSRM fallback.
 * Session/preview orchestration stays on [NavigationService].
 */
@Singleton
class DirectionsRouter @Inject constructor(
    mapsApiKeyProvider: MapsApiKeyProvider,
    private val http: NavigationHttp,
    private val motoTravelEstimator: MotoTravelEstimatorStore,
) {
    private val apiKey = mapsApiKeyProvider.getApiKey()

    /** Prefer Google; if empty, try OSRM once. */
    suspend fun fetchRoutes(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        alternatives: Boolean = false,
    ): List<DirectionsResult> {
        val google = fetchGoogleDirections(originLat, originLng, destLat, destLng, alternatives)
        if (google.isNotEmpty()) return google
        return listOfNotNull(fetchOsrmDirections(originLat, originLng, destLat, destLng))
    }

    private suspend fun fetchGoogleDirections(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        alternatives: Boolean,
    ): List<DirectionsResult> = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext emptyList()
        val url =
            "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=$originLat,$originLng&destination=$destLat,$destLng&mode=driving" +
                "&departure_time=now" +
                "${if (alternatives) "&alternatives=true" else ""}&key=$key"
        runCatching {
            val body = http.get(url) ?: return@runCatching emptyList()
            val json = JSONObject(body)
            val status = json.optString("status")
            if (status != "OK") {
                AppLogger.w(AppLogger.Category.UI, "Directions status=$status")
                return@runCatching emptyList()
            }
            val routesArray = json.getJSONArray("routes")
            buildList {
                for (ri in 0 until routesArray.length()) {
                    val route = routesArray.getJSONObject(ri)
                    val leg = route.getJSONArray("legs").getJSONObject(0)
                    val distance = leg.getJSONObject("distance").getDouble("value")
                    val duration = leg.getJSONObject("duration").getDouble("value")
                    val carTime = leg.optJSONObject("duration_in_traffic")
                        ?.optDouble("value", duration)
                        ?: duration
                    val estimate = motoTravelEstimator.estimate(distance, carTime)
                    val encoded = route.getJSONObject("overview_polyline").getString("points")
                    val coordinates = PolyUtil.decode(encoded).map {
                        RouteCoordinate(it.latitude, it.longitude)
                    }
                    val steps = buildList {
                        val stepsArray = leg.optJSONArray("steps") ?: return@buildList
                        for (i in 0 until stepsArray.length()) {
                            val step = stepsArray.getJSONObject(i)
                            val instruction = step.optString("html_instructions")
                                .replace(Regex("<[^>]+>"), " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                            if (instruction.isEmpty()) continue
                            val end = step.getJSONObject("end_location")
                            add(
                                NavStep(
                                    instruction = instruction,
                                    distanceMeters = step.getJSONObject("distance").getDouble("value"),
                                    endLatitude = end.getDouble("lat"),
                                    endLongitude = end.getDouble("lng"),
                                ),
                            )
                        }
                    }
                    add(
                        DirectionsResult(
                            distanceMeters = distance,
                            carTravelTimeSeconds = estimate.carTravelTimeSeconds,
                            motoTravelTimeSeconds = estimate.motoTravelTimeSeconds,
                            trafficDelaySeconds = estimate.trafficDelaySeconds,
                            coordinates = coordinates,
                            steps = steps,
                        ),
                    )
                }
            }
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Directions failed", it)
            emptyList()
        }
    }

    private suspend fun fetchOsrmDirections(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): DirectionsResult? = withContext(Dispatchers.IO) {
        val url =
            "https://router.project-osrm.org/route/v1/driving/" +
                "$originLng,$originLat;$destLng,$destLat" +
                "?overview=full&geometries=polyline&steps=true"
        runCatching {
            val body = http.get(url, userAgent = "MotoTripTracker/1.0") ?: return@runCatching null
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") {
                AppLogger.w(AppLogger.Category.UI, "OSRM code=${json.optString("code")}")
                return@runCatching null
            }
            val route = json.getJSONArray("routes").getJSONObject(0)
            val distance = route.getDouble("distance")
            val duration = route.getDouble("duration")
            val estimate = motoTravelEstimator.estimate(distance, duration)
            val encoded = route.getString("geometry")
            val coordinates = PolyUtil.decode(encoded).map { RouteCoordinate(it.latitude, it.longitude) }
            val steps = buildList {
                val legs = route.optJSONArray("legs") ?: return@buildList
                for (li in 0 until legs.length()) {
                    val stepsArray = legs.getJSONObject(li).optJSONArray("steps") ?: continue
                    for (si in 0 until stepsArray.length()) {
                        val step = stepsArray.getJSONObject(si)
                        val maneuver = step.optJSONObject("maneuver")
                        val type = maneuver?.optString("type").orEmpty()
                        val modifier = maneuver?.optString("modifier").orEmpty()
                        val name = step.optString("name")
                        val instruction = buildOsrmInstruction(type, modifier, name)
                        if (instruction.isBlank()) continue
                        val location = maneuver?.optJSONArray("location") ?: continue
                        add(
                            NavStep(
                                instruction = instruction,
                                distanceMeters = step.optDouble("distance", 0.0),
                                endLatitude = location.getDouble(1),
                                endLongitude = location.getDouble(0),
                            ),
                        )
                    }
                }
            }
            DirectionsResult(
                distanceMeters = distance,
                carTravelTimeSeconds = estimate.carTravelTimeSeconds,
                motoTravelTimeSeconds = estimate.motoTravelTimeSeconds,
                trafficDelaySeconds = estimate.trafficDelaySeconds,
                coordinates = coordinates,
                steps = steps,
            )
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "OSRM routing failed", it)
            null
        }
    }

    private fun buildOsrmInstruction(type: String, modifier: String, name: String): String {
        val road = name.takeIf { it.isNotBlank() }
        return when (type) {
            "depart" -> if (road != null) "Head onto $road" else "Depart"
            "arrive" -> "Arrive at destination"
            "turn" -> {
                val dir = when (modifier) {
                    "left" -> "Turn left"
                    "right" -> "Turn right"
                    "slight left" -> "Keep left"
                    "slight right" -> "Keep right"
                    "sharp left" -> "Sharp left"
                    "sharp right" -> "Sharp right"
                    "uturn" -> "Make a U-turn"
                    else -> "Turn"
                }
                if (road != null) "$dir onto $road" else dir
            }
            "new name" -> if (road != null) "Continue on $road" else "Continue"
            "merge" -> if (road != null) "Merge onto $road" else "Merge"
            "on ramp" -> if (road != null) "Take the ramp onto $road" else "Take the ramp"
            "off ramp" -> if (road != null) "Take the exit toward $road" else "Take the exit"
            "fork" -> if (modifier.contains("left")) "Keep left" else "Keep right"
            "roundabout", "rotary" ->
                if (road != null) "Enter the roundabout toward $road" else "Enter the roundabout"
            else -> road?.let { "Continue on $it" }.orEmpty()
        }
    }
}
