package com.odys.mototriptracker.data.weather

import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteWeatherSegment(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val etaEpochMs: Long,
    val temperatureC: Double?,
    val precipitationProbability: Int?,
    val conditionLabel: String
)

data class RouteWeatherState(
    val segments: List<RouteWeatherSegment> = emptyList(),
    val isLoading: Boolean = false,
    val hasRainAlongRoute: Boolean = false,
    val lastError: String? = null
) {
    val hasData: Boolean get() = segments.isNotEmpty()

    val summaryText: String
        get() = when {
            isLoading && segments.isEmpty() -> "Checking weather…"
            lastError != null && segments.isEmpty() -> lastError
            segments.isEmpty() -> "Weather unavailable"
            hasRainAlongRoute -> {
                val rain = segments.firstOrNull { (it.precipitationProbability ?: 0) >= 40 }
                if (rain != null) {
                    "Rain likely near ${rain.label} · ${rain.temperatureC?.toInt() ?: 0}°C at destination"
                } else summaryFromDestination()
            }
            else -> summaryFromDestination()
        }

    private fun summaryFromDestination(): String {
        val dest = segments.lastOrNull() ?: return "Weather loaded"
        val temp = dest.temperatureC?.toInt() ?: return dest.conditionLabel
        return "${dest.conditionLabel} · ${temp}°C at destination"
    }
}

@Singleton
class RouteWeatherService @Inject constructor() {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(RouteWeatherState())
    val state: StateFlow<RouteWeatherState> = _state.asStateFlow()

    private var lastFetchKey: String? = null
    private var cachedCoordinates: List<RouteCoordinate> = emptyList()
    private var cachedTravelTimeSeconds = 0.0

    fun clear() {
        lastFetchKey = null
        cachedCoordinates = emptyList()
        cachedTravelTimeSeconds = 0.0
        _state.value = RouteWeatherState()
    }

    fun refreshForRoute(coordinates: List<RouteCoordinate>, travelTimeSeconds: Double) {
        if (coordinates.size < 2) {
            clear()
            return
        }
        val key = routeKey(coordinates, travelTimeSeconds)
        if (key == lastFetchKey && _state.value.hasData) return

        cachedCoordinates = coordinates
        cachedTravelTimeSeconds = travelTimeSeconds.coerceAtLeast(60.0)
        lastFetchKey = key
        _state.update { it.copy(isLoading = true, lastError = null) }

        scope.launch {
            val samples = samplePoints(coordinates, 5)
            val departureMs = System.currentTimeMillis()
            val built = samples.mapIndexed { index, sample ->
                val fraction = if (samples.size > 1) index.toDouble() / (samples.size - 1) else 0.0
                val etaMs = departureMs + (cachedTravelTimeSeconds * fraction * 1000).toLong()
                val label = labelFor(index, samples.size)
                fetchSegment(label, sample.latitude, sample.longitude, etaMs)
            }.filterNotNull()

            _state.update {
                it.copy(
                    segments = built,
                    hasRainAlongRoute = built.any { seg -> (seg.precipitationProbability ?: 0) >= 40 },
                    isLoading = false,
                    lastError = if (built.isEmpty()) "Could not load weather — check connection" else null
                )
            }
            if (built.isNotEmpty()) {
                AppLogger.i(AppLogger.Category.UI, "Route weather loaded ${built.size} segments")
            }
        }
    }

    private suspend fun fetchSegment(
        label: String,
        latitude: Double,
        longitude: Double,
        etaMs: Long
    ): RouteWeatherSegment? = withContext(Dispatchers.IO) {
        val url =
            "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$latitude&longitude=$longitude" +
                "&hourly=temperature_2m,precipitation_probability,weather_code" +
                "&forecast_days=2&timezone=auto"
        runCatching {
            val body = httpClient.newCall(Request.Builder().url(url).get().build())
                .execute().body?.string() ?: return@runCatching null
            val json = JSONObject(body)
            val hourly = json.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temps = hourly.optJSONArray("temperature_2m")
            val rain = hourly.optJSONArray("precipitation_probability")
            val codes = hourly.optJSONArray("weather_code")

            val tz = json.optString("timezone").takeIf { it.isNotBlank() }?.let { TimeZone.getTimeZone(it) }
                ?: TimeZone.getDefault()
            val targetHour = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = tz }
                .format(Date(etaMs))

            var bestIndex = 0
            var bestDelta = Long.MAX_VALUE
            for (i in 0 until times.length()) {
                val hour = times.getString(i)
                val delta = kotlin.math.abs(parseHour(hour, tz) - etaMs)
                if (delta < bestDelta) {
                    bestDelta = delta
                    bestIndex = i
                }
            }

            RouteWeatherSegment(
                label = label,
                latitude = latitude,
                longitude = longitude,
                etaEpochMs = etaMs,
                temperatureC = temps?.optDouble(bestIndex),
                precipitationProbability = rain?.optInt(bestIndex),
                conditionLabel = weatherLabel(codes?.optInt(bestIndex) ?: 0)
            )
        }.getOrNull()
    }

    private fun parseHour(text: String, tz: TimeZone): Long {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        fmt.timeZone = tz
        return fmt.parse(text)?.time ?: 0L
    }

    private fun weatherLabel(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> "Rain"
        71, 73, 75, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy"
    }

    private fun samplePoints(coordinates: List<RouteCoordinate>, count: Int): List<RouteCoordinate> {
        if (coordinates.size < 2) return coordinates
        val target = count.coerceIn(2, coordinates.size)
        val cumulative = mutableListOf(0.0)
        for (i in 0 until coordinates.lastIndex) {
            cumulative += cumulative.last() + haversine(
                coordinates[i], coordinates[i + 1]
            )
        }
        val total = cumulative.last()
        if (total <= 0) return listOf(coordinates.first())

        return (0 until target).map { i ->
            val goal = total * i / maxOf(target - 1, 1)
            var segmentIndex = 0
            while (segmentIndex < cumulative.lastIndex && cumulative[segmentIndex + 1] < goal) {
                segmentIndex++
            }
            val segStart = cumulative[segmentIndex]
            val segEnd = cumulative[segmentIndex + 1]
            val t = if (segEnd > segStart) (goal - segStart) / (segEnd - segStart) else 0.0
            val a = coordinates[segmentIndex]
            val b = coordinates[(segmentIndex + 1).coerceAtMost(coordinates.lastIndex)]
            RouteCoordinate(
                latitude = a.latitude + (b.latitude - a.latitude) * t,
                longitude = a.longitude + (b.longitude - a.longitude) * t
            )
        }
    }

    private fun labelFor(index: Int, total: Int): String = when (index) {
        0 -> "Start"
        total - 1 -> "Destination"
        else -> "En route"
    }

    private fun routeKey(coordinates: List<RouteCoordinate>, travelTime: Double): String {
        val rounded = coordinates.take(8).joinToString("|") {
            String.format(Locale.US, "%.3f,%.3f", it.latitude, it.longitude)
        }
        return "$rounded-${travelTime.toInt()}"
    }

    private fun haversine(a: RouteCoordinate, b: RouteCoordinate): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return earthRadius * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}
