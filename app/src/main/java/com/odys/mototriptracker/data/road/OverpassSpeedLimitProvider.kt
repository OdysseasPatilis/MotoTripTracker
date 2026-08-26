package com.odys.mototriptracker.data.road

import com.odys.mototriptracker.domain.SpeedLimitParser
import com.odys.mototriptracker.domain.SpeedLimitProvider
import com.odys.mototriptracker.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches road [maxspeed] from OpenStreetMap via public Overpass mirrors.
 *
 * Android emulators (and some mobile networks) often fail on IPv6-only routes to
 * overpass-api.de. We prefer IPv4 DNS results and rotate mirrors on failure.
 */
@Singleton
class OverpassSpeedLimitProvider @Inject constructor() : SpeedLimitProvider {

    @Volatile
    private var preferredEndpointIndex = 0

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Ipv4PreferringDns)
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

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
        val endpoints = rotatedEndpoints()

        for ((indexInRotation, endpoint) in endpoints.withIndex()) {
            val absoluteIndex = (preferredEndpointIndex + indexInRotation) % OVERPASS_ENDPOINTS.size
            val result = requestMaxSpeed(endpoint, query, radiusMeters)
            if (result != null) {
                preferredEndpointIndex = absoluteIndex
                return result
            }
        }

        AppLogger.w(
            AppLogger.Category.SPEED_LIMIT,
            "All Overpass mirrors failed @ ${"%.5f".format(lat)},${"%.5f".format(lng)}"
        )
        return null
    }

    private fun rotatedEndpoints(): List<String> {
        val list = OVERPASS_ENDPOINTS.toMutableList()
        if (preferredEndpointIndex in list.indices) {
            val preferred = list.removeAt(preferredEndpointIndex)
            list.add(0, preferred)
        }
        return list
    }

    private fun requestMaxSpeed(endpoint: String, query: String, radiusMeters: Int): String? {
        val body = FormBody.Builder()
            .add("data", query)
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(
                        AppLogger.Category.SPEED_LIMIT,
                        "Overpass HTTP ${response.code} from ${hostOf(endpoint)} (r=${radiusMeters}m)"
                    )
                    return null
                }
                val payload = response.body?.string().orEmpty()
                if (payload.isBlank()) return null
                parseBestMaxSpeed(payload)
            }
        } catch (e: Exception) {
            AppLogger.w(
                AppLogger.Category.SPEED_LIMIT,
                "Overpass ${hostOf(endpoint)} failed: ${e.message}"
            )
            null
        }
    }

    private fun hostOf(endpoint: String): String =
        endpoint.toHttpUrlOrNull()?.host ?: endpoint

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

    /**
     * Prefer IPv4 addresses so emulators / broken IPv6 routes don't hang on AAAA records.
     */
    private object Ipv4PreferringDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val ipv4 = all.filterIsInstance<Inet4Address>()
            return if (ipv4.isNotEmpty()) ipv4 else all
        }
    }

    companion object {
        private val OVERPASS_ENDPOINTS = listOf(
            "https://lz4.overpass-api.de/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass-api.de/api/interpreter"
        )

        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val READ_TIMEOUT_MS = 10_000L
        private const val CALL_TIMEOUT_MS = 12_000L
        private const val USER_AGENT = "MotoTripTracker/1.0 (Android; motorcycle trip tracker)"
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
