package com.odys.mototriptracker.data.road

import com.odys.mototriptracker.di.AppHttpClient
import com.odys.mototriptracker.domain.PostedSpeedLimitLookup
import com.odys.mototriptracker.domain.PostedSpeedLimitSource
import com.odys.mototriptracker.util.AppLogger
import com.odys.mototriptracker.util.MapsApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class RoadsSnapPoint(
    val latitude: Double,
    val longitude: Double,
    val placeId: String?,
    val originalIndex: Int?,
)

/**
 * Google Roads API: [snapToRoads](https://developers.google.com/maps/documentation/roads/snap)
 * and optional [speedLimits](https://developers.google.com/maps/documentation/roads/speed-limits)
 * (Asset Tracking license required for speed limits).
 */
@Singleton
class GoogleRoadsClient @Inject constructor(
    mapsApiKeyProvider: MapsApiKeyProvider,
    @param:AppHttpClient private val httpClient: OkHttpClient,
) : PostedSpeedLimitSource {
    private val apiKey = mapsApiKeyProvider.getApiKey()

    @Volatile
    override var isDisabled: Boolean = false
        private set

    suspend fun snapToRoads(
        path: List<Pair<Double, Double>>,
    ): List<RoadsSnapPoint> = withContext(Dispatchers.IO) {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        if (path.isEmpty()) return@withContext emptyList()
        val pathParam = path.joinToString("|") { (lat, lng) -> "$lat,$lng" }
        val encoded = URLEncoder.encode(pathParam, Charsets.UTF_8.name())
        val url =
            "https://roads.googleapis.com/v1/snapToRoads?path=$encoded&interpolate=false&key=$key"
        runCatching {
            val body = get(url) ?: return@runCatching emptyList()
            val json = JSONObject(body)
            if (json.has("error")) {
                AppLogger.w(
                    AppLogger.Category.SPEED_LIMIT,
                    "snapToRoads error=${json.optJSONObject("error")?.optString("message")}"
                )
                return@runCatching emptyList()
            }
            val points = json.optJSONArray("snappedPoints") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until points.length()) {
                    val point = points.getJSONObject(i)
                    val location = point.getJSONObject("location")
                    add(
                        RoadsSnapPoint(
                            latitude = location.getDouble("latitude"),
                            longitude = location.getDouble("longitude"),
                            placeId = point.optString("placeId").takeIf { it.isNotBlank() },
                            originalIndex = if (point.has("originalIndex")) {
                                point.getInt("originalIndex")
                            } else {
                                null
                            },
                        )
                    )
                }
            }
        }.getOrElse {
            AppLogger.w(AppLogger.Category.SPEED_LIMIT, "snapToRoads failed", it)
            emptyList()
        }
    }

    override suspend fun lookupPlaceId(placeId: String): PostedSpeedLimitLookup =
        withContext(Dispatchers.IO) {
            if (isDisabled) return@withContext PostedSpeedLimitLookup.Unavailable
            val key = apiKey?.takeIf { it.isNotBlank() }
                ?: return@withContext PostedSpeedLimitLookup.Failed
            if (placeId.isBlank()) return@withContext PostedSpeedLimitLookup.Missing
            val encoded = URLEncoder.encode(placeId, Charsets.UTF_8.name())
            val url =
                "https://roads.googleapis.com/v1/speedLimits?placeId=$encoded&units=KPH&key=$key"
            runCatching {
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when (response.code) {
                        403, 401 -> {
                            AppLogger.w(
                                AppLogger.Category.SPEED_LIMIT,
                                "Roads speedLimits unavailable (HTTP ${response.code}) — disabling"
                            )
                            isDisabled = true
                            return@runCatching PostedSpeedLimitLookup.Unavailable
                        }
                        !in 200..299 -> {
                            AppLogger.w(
                                AppLogger.Category.SPEED_LIMIT,
                                "Roads speedLimits HTTP ${response.code}"
                            )
                            return@runCatching PostedSpeedLimitLookup.Failed
                        }
                    }
                    val json = JSONObject(body)
                    if (json.has("error")) {
                        val status = json.optJSONObject("error")?.optInt("code") ?: 0
                        val message = json.optJSONObject("error")?.optString("message").orEmpty()
                        if (status == 403 || message.contains("Asset Tracking", ignoreCase = true) ||
                            message.contains("PERMISSION_DENIED", ignoreCase = true)
                        ) {
                            isDisabled = true
                            AppLogger.w(
                                AppLogger.Category.SPEED_LIMIT,
                                "Roads speedLimits not licensed — disabling ($message)"
                            )
                            return@runCatching PostedSpeedLimitLookup.Unavailable
                        }
                        return@runCatching PostedSpeedLimitLookup.Failed
                    }
                    val limits = json.optJSONArray("speedLimits")
                    if (limits == null || limits.length() == 0) {
                        return@runCatching PostedSpeedLimitLookup.Missing
                    }
                    val first = limits.getJSONObject(0)
                    val kmh = first.optInt("speedLimit", -1)
                    if (kmh in 5..200) PostedSpeedLimitLookup.Value(kmh)
                    else PostedSpeedLimitLookup.Missing
                }
            }.getOrElse {
                AppLogger.w(AppLogger.Category.SPEED_LIMIT, "Roads speedLimits failed", it)
                PostedSpeedLimitLookup.Failed
            }
        }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.w(
                    AppLogger.Category.SPEED_LIMIT,
                    "Roads HTTP ${response.code} for ${url.substringBefore('?')}"
                )
                return null
            }
            return response.body?.string()
        }
    }
}
