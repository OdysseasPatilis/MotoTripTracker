package com.odys.mototriptracker.data.petrol

import com.odys.mototriptracker.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class PetrolStationRecommendation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val brand: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val openStatus: OpeningHoursEvaluator.Status,
    val availableOctanes: Set<Int>,
    val openingHoursRaw: String?,
    val isHighwayAccessible: Boolean,
    val googlePlaceId: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val websiteUri: String? = null,
    val googleMapsUri: String? = null,
    val weekdayHours: List<String> = emptyList(),
    val hoursFromGoogle: Boolean = false
) {
    fun displayOctanes(preferred: Set<Int>): String {
        val hits = availableOctanes.intersect(preferred).sorted()
        if (hits.isNotEmpty()) return hits.joinToString(" · ")
        if (availableOctanes.isEmpty()) return "Fuel grades unknown"
        return availableOctanes.sorted().joinToString(" · ")
    }

    fun displayHours(): String {
        if (weekdayHours.isNotEmpty()) return weekdayHours.joinToString("\n")
        return openingHoursRaw?.takeIf { it.isNotBlank() } ?: "Hours unknown"
    }
}

data class RankedPetrolStation(
    val recommendation: PetrolStationRecommendation,
    val score: Int
)

data class PetrolSearchResult(
    val plan: PetrolSearchPlan,
    val stations: List<RankedPetrolStation>
)

@Singleton
class PetrolStationFinder @Inject constructor(
    private val placesEnricher: PetrolPlacesEnricher
) {
    private val httpClient = OkHttpClient.Builder()
        .dns(Ipv4PreferringDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        latitude: Double,
        longitude: Double,
        preferences: PetrolPreferences,
        speedKmh: Double = 0.0,
        courseDegrees: Float? = null,
        includeClosed: Boolean = false
    ): PetrolSearchResult = withContext(Dispatchers.IO) {
        val fetch = fetchOsmData(latitude, longitude, MAX_FETCH_RADIUS_METERS)
        val isNearMotorway = isWithinHighwayProximity(
            latitude, longitude, fetch.motorwaySegments, HIGHWAY_PROXIMITY_METERS
        )

        val distances = fetch.stations.map { station ->
            haversineMeters(latitude, longitude, station.latitude, station.longitude)
        }
        val statuses = fetch.stations.map { OpeningHoursEvaluator.status(it.openingHours) }
        val countableDistances = distances.zip(statuses).mapNotNull { (distance, status) ->
            if (!includeClosed && status == OpeningHoursEvaluator.Status.CLOSED) null else distance
        }

        val tierRadii = listOf(2_000, 5_000, 10_000, 20_000, MAX_FETCH_RADIUS_METERS)
        val counts = PetrolSearchStrategy.countStations(tierRadii, countableDistances)
        val plan = PetrolSearchStrategy.plan(speedKmh, isNearMotorway, counts)

        var ranked = fetch.stations.mapIndexed { index, station ->
            val distance = distances[index]
            val status = statuses[index]
            val highwayAccessible = station.isHighwayTagged || isWithinHighwayProximity(
                station.latitude, station.longitude, fetch.motorwaySegments, HIGHWAY_PROXIMITY_METERS
            )
            val recommendation = PetrolStationRecommendation(
                name = station.displayName,
                brand = station.brand,
                latitude = station.latitude,
                longitude = station.longitude,
                distanceMeters = distance,
                openStatus = status,
                availableOctanes = station.octanes,
                openingHoursRaw = station.openingHours,
                isHighwayAccessible = highwayAccessible
            )
            RankedPetrolStation(
                recommendation = recommendation,
                score = preferenceScore(
                    recommendation, preferences, plan, latitude, longitude, courseDegrees
                )
            )
        }

        ranked = ranked.filter { it.recommendation.distanceMeters <= plan.activeRadiusMeters }

        val googlePlaces = placesEnricher.findNearbyGasStations(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = plan.activeRadiusMeters
        )
        ranked = ranked.map { rankedStation ->
            val match = placesEnricher.nearestMatch(
                rankedStation.recommendation.latitude,
                rankedStation.recommendation.longitude,
                googlePlaces
            ) ?: return@map rankedStation
            val googleStatus = when (match.isOpenNow) {
                true -> OpeningHoursEvaluator.Status.OPEN
                false -> OpeningHoursEvaluator.Status.CLOSED
                null -> GoogleWeekdayHoursParser.statusNow(match.weekdayHours).takeUnless {
                    it == OpeningHoursEvaluator.Status.UNKNOWN
                } ?: rankedStation.recommendation.openStatus
            }
            val enriched = rankedStation.recommendation.copy(
                openStatus = googleStatus,
                googlePlaceId = match.placeId,
                address = match.address ?: rankedStation.recommendation.address,
                rating = match.rating,
                ratingCount = match.ratingCount,
                weekdayHours = match.weekdayHours,
                hoursFromGoogle = match.weekdayHours.isNotEmpty() || match.isOpenNow != null
            )
            rankedStation.copy(
                recommendation = enriched,
                score = preferenceScore(
                    enriched, preferences, plan, latitude, longitude, courseDegrees
                )
            )
        }

        // Always resolve Google open/closed for matched stations (nearby cannot compute isOpen alone).
        val googleIdsNeedingOpen = ranked
            .mapNotNull { it.recommendation.googlePlaceId }
            .distinct()
        if (googleIdsNeedingOpen.isNotEmpty()) {
            val openInfo = placesEnricher.fetchOpenInfo(googleIdsNeedingOpen)
            ranked = ranked.map { rankedStation ->
                val placeId = rankedStation.recommendation.googlePlaceId ?: return@map rankedStation
                val info = openInfo[placeId]
                val status = when (info?.isOpenNow) {
                    true -> OpeningHoursEvaluator.Status.OPEN
                    false -> OpeningHoursEvaluator.Status.CLOSED
                    null -> {
                        val hours = info?.weekdayHours
                            ?.takeIf { it.isNotEmpty() }
                            ?: rankedStation.recommendation.weekdayHours
                        when (GoogleWeekdayHoursParser.statusNow(hours)) {
                            OpeningHoursEvaluator.Status.OPEN -> OpeningHoursEvaluator.Status.OPEN
                            OpeningHoursEvaluator.Status.CLOSED -> OpeningHoursEvaluator.Status.CLOSED
                            OpeningHoursEvaluator.Status.UNKNOWN -> rankedStation.recommendation.openStatus
                        }
                    }
                }
                val weekdayHours = info?.weekdayHours?.takeIf { it.isNotEmpty() }
                    ?: rankedStation.recommendation.weekdayHours
                val enriched = rankedStation.recommendation.copy(
                    openStatus = status,
                    weekdayHours = weekdayHours,
                    hoursFromGoogle = status != OpeningHoursEvaluator.Status.UNKNOWN ||
                        weekdayHours.isNotEmpty() ||
                        rankedStation.recommendation.hoursFromGoogle
                )
                rankedStation.copy(
                    recommendation = enriched,
                    score = preferenceScore(
                        enriched, preferences, plan, latitude, longitude, courseDegrees
                    )
                )
            }
        }

        if (ranked.isEmpty() && googlePlaces.isNotEmpty()) {
            ranked = googlePlaces.map { place ->
                val distance = haversineMeters(latitude, longitude, place.latitude, place.longitude)
                val recommendation = PetrolStationRecommendation(
                    name = place.name,
                    brand = null,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    distanceMeters = distance,
                    openStatus = OpeningHoursEvaluator.Status.UNKNOWN,
                    availableOctanes = emptySet(),
                    openingHoursRaw = null,
                    isHighwayAccessible = false,
                    googlePlaceId = place.placeId,
                    address = place.address,
                    rating = place.rating,
                    ratingCount = place.ratingCount,
                    weekdayHours = place.weekdayHours,
                    hoursFromGoogle = place.weekdayHours.isNotEmpty()
                )
                RankedPetrolStation(
                    recommendation = recommendation,
                    score = preferenceScore(
                        recommendation, preferences, plan, latitude, longitude, courseDegrees
                    )
                )
            }.filter { it.recommendation.distanceMeters <= plan.activeRadiusMeters }

            val googleOnlyIds = ranked.mapNotNull { it.recommendation.googlePlaceId }.distinct()
            if (googleOnlyIds.isNotEmpty()) {
                val openInfo = placesEnricher.fetchOpenInfo(googleOnlyIds)
                ranked = ranked.map { rankedStation ->
                    val placeId = rankedStation.recommendation.googlePlaceId ?: return@map rankedStation
                    val info = openInfo[placeId]
                    val status = when (info?.isOpenNow) {
                        true -> OpeningHoursEvaluator.Status.OPEN
                        false -> OpeningHoursEvaluator.Status.CLOSED
                        null -> GoogleWeekdayHoursParser.statusNow(
                            info?.weekdayHours?.takeIf { it.isNotEmpty() }
                                ?: rankedStation.recommendation.weekdayHours
                        )
                    }
                    val weekdayHours = info?.weekdayHours?.takeIf { it.isNotEmpty() }
                        ?: rankedStation.recommendation.weekdayHours
                    val enriched = rankedStation.recommendation.copy(
                        openStatus = status,
                        weekdayHours = weekdayHours,
                        hoursFromGoogle = true
                    )
                    rankedStation.copy(
                        recommendation = enriched,
                        score = preferenceScore(
                            enriched, preferences, plan, latitude, longitude, courseDegrees
                        )
                    )
                }
            }
        }

        if (!includeClosed) {
            ranked = ranked.filter { it.recommendation.openStatus != OpeningHoursEvaluator.Status.CLOSED }
        }

        ranked = ranked.sortedWith(
            compareBy({ it.score }, { it.recommendation.distanceMeters })
        )

        AppLogger.i(
            AppLogger.Category.UI,
            "Petrol search ${plan.summary}: ${ranked.size} stations " +
                "(OSM=${fetch.stations.size}, Google=${googlePlaces.size}, highwayBias=${plan.prioritizeHighway})"
        )
        PetrolSearchResult(plan, ranked)
    }

    suspend fun fetchGoogleDetails(
        placeId: String?,
        latitude: Double,
        longitude: Double
    ): GooglePetrolDetails? =
        placesEnricher.fetchDetails(placeId, latitude, longitude)

    /** Lower is better. */
    private fun preferenceScore(
        station: PetrolStationRecommendation,
        preferences: PetrolPreferences,
        plan: PetrolSearchPlan,
        originLat: Double,
        originLng: Double,
        courseDegrees: Float?
    ): Int {
        var score = preferences.brandRank(station.brand ?: station.name) * 1_000

        val preferred = preferences.preferredOctanes.value
        score += when {
            preferred.isEmpty() -> 0
            station.availableOctanes.isEmpty() -> 200
            station.availableOctanes.intersect(preferred).isEmpty() -> 500
            else -> 0
        }

        score += when (station.openStatus) {
            OpeningHoursEvaluator.Status.OPEN -> 0
            OpeningHoursEvaluator.Status.UNKNOWN -> 50
            OpeningHoursEvaluator.Status.CLOSED -> 5_000
        }

        if (plan.prioritizeHighway) {
            score += if (station.isHighwayAccessible) -800 else 350
            val course = courseDegrees
            if (course != null && course >= 0f) {
                val bearing = bearingDegrees(originLat, originLng, station.latitude, station.longitude)
                val delta = angularDifference(bearing, course.toDouble())
                score += when {
                    delta <= 55 -> -250
                    delta >= 120 -> 200
                    else -> 0
                }
            }
        }

        score += (min(station.distanceMeters, 20_000.0) / 50).toInt()
        return score
    }

    private fun fetchOsmData(lat: Double, lng: Double, radiusMeters: Int): OsmFetchResult {
        val query = """
            [out:json][timeout:25];
            (
              node["amenity"="fuel"](around:$radiusMeters,$lat,$lng);
              way["amenity"="fuel"](around:$radiusMeters,$lat,$lng);
            );
            out center tags;
            way["highway"~"^(motorway|trunk|motorway_link|trunk_link)$"](around:$MOTORWAY_PROBE_METERS,$lat,$lng);
            out geom;
        """.trimIndent()

        for (endpoint in OVERPASS_ENDPOINTS) {
            val body = FormBody.Builder().add("data", query).build()
            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val result = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLogger.w(AppLogger.Category.UI, "Petrol Overpass HTTP ${response.code} from $endpoint")
                        return@runCatching null
                    }
                    parseOsmResponse(response.body?.string().orEmpty())
                }
            }.getOrElse {
                AppLogger.w(AppLogger.Category.UI, "Petrol Overpass $endpoint failed", it)
                null
            }
            if (result != null) return result
        }
        return OsmFetchResult(emptyList(), emptyList())
    }

    private fun parseOsmResponse(payload: String): OsmFetchResult {
        if (payload.isBlank()) return OsmFetchResult(emptyList(), emptyList())
        val elements = JSONObject(payload).optJSONArray("elements") ?: return OsmFetchResult(emptyList(), emptyList())
        val stations = mutableListOf<OsmFuelStation>()
        val motorways = mutableListOf<MotorwaySegment>()

        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags")
            val type = el.optString("type")
            val geometry = el.optJSONArray("geometry")

            if (type == "way" && geometry != null && geometry.length() > 0 &&
                tags?.optString("amenity") != "fuel"
            ) {
                val coords = buildList {
                    for (g in 0 until geometry.length()) {
                        val pt = geometry.getJSONObject(g)
                        add(pt.getDouble("lat") to pt.getDouble("lon"))
                    }
                }
                motorways += MotorwaySegment(coords)
                continue
            }

            val stationLat = el.optDouble("lat", Double.NaN).takeIf { !it.isNaN() }
                ?: el.optJSONObject("center")?.optDouble("lat")
                ?: continue
            val stationLng = el.optDouble("lon", Double.NaN).takeIf { !it.isNaN() }
                ?: el.optJSONObject("center")?.optDouble("lon")
                ?: continue

            if (tags == null || tags.optString("amenity") != "fuel") continue
            val tagMap = tags.toStringMap()
            stations += OsmFuelStation(
                name = tagMap["name"],
                brand = tagMap["brand"] ?: tagMap["operator"],
                latitude = stationLat,
                longitude = stationLng,
                openingHours = tagMap["opening_hours"],
                octanes = parseOctanes(tagMap),
                isHighwayTagged = isHighwayTagged(tagMap)
            )
        }
        return OsmFetchResult(stations, motorways)
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        keys().forEach { key -> map[key] = optString(key) }
        return map
    }

    private data class OsmFuelStation(
        val name: String?,
        val brand: String?,
        val latitude: Double,
        val longitude: Double,
        val openingHours: String?,
        val octanes: Set<Int>,
        val isHighwayTagged: Boolean
    ) {
        val displayName: String
            get() = when {
                brand != null && name != null && brand != name -> "$brand · $name"
                brand != null -> brand
                name != null -> name
                else -> "Petrol station"
            }
    }

    private data class MotorwaySegment(val coordinates: List<Pair<Double, Double>>)
    private data class OsmFetchResult(
        val stations: List<OsmFuelStation>,
        val motorwaySegments: List<MotorwaySegment>
    )

    private object Ipv4PreferringDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val ipv4 = all.filterIsInstance<Inet4Address>()
            return if (ipv4.isNotEmpty()) ipv4 else all
        }
    }

    companion object {
        private const val MAX_FETCH_RADIUS_METERS = 50_000
        private const val MOTORWAY_PROBE_METERS = 1_200
        private const val HIGHWAY_PROXIMITY_METERS = 400.0
        private const val USER_AGENT = "MotoTripTracker/1.0 (Android; petrol search)"

        private val OVERPASS_ENDPOINTS = listOf(
            "https://lz4.overpass-api.de/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass-api.de/api/interpreter"
        )

        private val TRUTHY = setOf("yes", "true", "1", "ok")

        fun parseOctanes(tags: Map<String, String>): Set<Int> {
            val result = mutableSetOf<Int>()
            fun consider(key: String, octane: Int) {
                val value = tags[key]?.lowercase().orEmpty()
                if (value in TRUTHY || value == octane.toString()) result += octane
            }
            consider("fuel:octane_95", 95)
            consider("fuel:octane_98", 98)
            consider("fuel:octane_100", 100)
            consider("fuel:95", 95)
            consider("fuel:98", 98)
            consider("fuel:100", 100)
            tags.forEach { (key, value) ->
                if (!key.startsWith("fuel:octane_")) return@forEach
                if (value.lowercase() !in TRUTHY && value != "yes") return@forEach
                key.removePrefix("fuel:octane_").toIntOrNull()?.let { result += it }
            }
            return result
        }

        private fun isHighwayTagged(tags: Map<String, String>): Boolean {
            val motorway = tags["motorway"]?.lowercase()
            if (motorway == "yes" || motorway == "designated") return true
            if (tags["highway"]?.lowercase() == "rest_area") return true
            if (tags["access"]?.lowercase() == "motorway") return true
            return false
        }

        private fun isWithinHighwayProximity(
            lat: Double,
            lng: Double,
            motorways: List<MotorwaySegment>,
            maxMeters: Double
        ): Boolean {
            for (segment in motorways) {
                if (segment.coordinates.size < 2) continue
                for (i in 0 until segment.coordinates.lastIndex) {
                    val start = segment.coordinates[i]
                    val end = segment.coordinates[i + 1]
                    if (distanceToSegment(lat, lng, start.first, start.second, end.first, end.second) <= maxMeters) {
                        return true
                    }
                }
            }
            return false
        }

        private fun distanceToSegment(
            plat: Double, plng: Double,
            alat: Double, alng: Double,
            blat: Double, blng: Double
        ): Double {
            val segLen = haversineMeters(alat, alng, blat, blng)
            if (segLen <= 0) return haversineMeters(plat, plng, alat, alng)
            val dx = blat - alat
            val dy = blng - alng
            val t = (((plat - alat) * dx + (plng - alng) * dy) / (dx.pow(2) + dy.pow(2)))
                .coerceIn(0.0, 1.0)
            return haversineMeters(plat, plng, alat + t * dx, alng + t * dy)
        }

        private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earth = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return earth * 2 * atan2(sqrt(a), sqrt(1 - a))
        }

        private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val φ1 = Math.toRadians(lat1)
            val φ2 = Math.toRadians(lat2)
            val Δλ = Math.toRadians(lon2 - lon1)
            val y = sin(Δλ) * cos(φ2)
            val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
            val deg = Math.toDegrees(atan2(y, x))
            return if (deg >= 0) deg else deg + 360
        }

        private fun angularDifference(a: Double, b: Double): Double {
            val diff = kotlin.math.abs(a - b) % 360
            return if (diff > 180) 360 - diff else diff
        }
    }
}
