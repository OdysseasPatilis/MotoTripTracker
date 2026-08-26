package com.odys.mototriptracker.data.road

import com.odys.mototriptracker.domain.SpeedLimitParser
import com.odys.mototriptracker.domain.SpeedLimitProvider
import com.odys.mototriptracker.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverpassSpeedLimitProvider @Inject constructor() : SpeedLimitProvider {

    override suspend fun getSpeedLimitKmh(latitude: Double, longitude: Double): Int? =
        withContext(Dispatchers.IO) {
            for (radiusMeters in QUERY_RADII_METERS) {
                val rawLimit = queryNearestMaxSpeed(latitude, longitude, radiusMeters) ?: continue
                val parsed = SpeedLimitParser.parse(rawLimit)
                if (parsed != null) {
                    AppLogger.d(
                        AppLogger.Category.SPEED_LIMIT,
                        "Overpass raw='$rawLimit' → $parsed km/h (r=${radiusMeters}m)"
                    )
                    return@withContext parsed
                }
                AppLogger.d(
                    AppLogger.Category.SPEED_LIMIT,
                    "Overpass raw='$rawLimit' unparseable (r=${radiusMeters}m)"
                )
            }
            null
        }

    private fun queryNearestMaxSpeed(lat: Double, lng: Double, radiusMeters: Int): String? {
        val query = buildQuery(lat, lng, radiusMeters)
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val connection = (URL("$OVERPASS_ENDPOINT?data=$encodedQuery").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                AppLogger.w(AppLogger.Category.SPEED_LIMIT, "Overpass HTTP $code (r=${radiusMeters}m)")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseBestMaxSpeed(body)
        } catch (e: Exception) {
            AppLogger.w(AppLogger.Category.SPEED_LIMIT, "Overpass request failed: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun buildQuery(lat: Double, lng: Double, radiusMeters: Int): String = """
        [out:json][timeout:10];
        (
          way(around:$radiusMeters,$lat,$lng)["highway"]["maxspeed"];
        );
        out tags;
    """.trimIndent()

    private fun parseBestMaxSpeed(jsonBody: String): String? {
        val elements = JSONObject(jsonBody).optJSONArray("elements") ?: return null
        if (elements.length() == 0) return null

        var bestLimit: String? = null
        var bestPriority = Int.MIN_VALUE

        for (index in 0 until elements.length()) {
            val element = elements.getJSONObject(index)
            val tags = element.optJSONObject("tags") ?: continue
            val maxspeed = tags.optString("maxspeed").takeIf { it.isNotBlank() } ?: continue
            val highway = tags.optString("highway")
            val priority = HIGHWAY_PRIORITY[highway] ?: DEFAULT_HIGHWAY_PRIORITY
            if (priority >= bestPriority) {
                bestPriority = priority
                bestLimit = maxspeed
            }
        }

        return bestLimit
    }

    companion object {
        private const val OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val USER_AGENT = "MotoTripTracker/1.0"
        private const val DEFAULT_HIGHWAY_PRIORITY = 1
        private val QUERY_RADII_METERS = intArrayOf(30, 60)

        private val HIGHWAY_PRIORITY = mapOf(
            "motorway" to 100,
            "motorway_link" to 95,
            "trunk" to 90,
            "trunk_link" to 85,
            "primary" to 80,
            "primary_link" to 75,
            "secondary" to 70,
            "secondary_link" to 65,
            "tertiary" to 60,
            "tertiary_link" to 55,
            "unclassified" to 40,
            "residential" to 35,
            "living_street" to 30,
            "service" to 20
        )
    }
}
