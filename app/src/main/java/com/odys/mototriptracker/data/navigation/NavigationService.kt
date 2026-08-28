package com.odys.mototriptracker.data.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.maps.android.PolyUtil
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.util.AppLogger
import com.odys.mototriptracker.util.MapsApiKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class NavigationService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    mapsApiKeyProvider: MapsApiKeyProvider
) {
    private val apiKey = mapsApiKeyProvider.getApiKey()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private var originLat: Double? = null
    private var originLng: Double? = null
    private var totalRouteDistanceMeters = 0.0
    private var totalTravelTimeSeconds = 0.0
    private var searchJob: Job? = null
    private var routeJob: Job? = null

    fun updateSearchQuery(query: String) {
        if (query == _state.value.searchQuery) return
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            searchJob?.cancel()
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            val results = fetchAutocomplete(query)
            _state.update { it.copy(searchResults = results) }
        }
    }

    fun updateOrigin(latitude: Double, longitude: Double) {
        originLat = latitude
        originLng = longitude
        if (_state.value.hasDestination && !_state.value.hasRoute && !_state.value.isRouting) {
            computeRoute()
        }
        recomputeRemaining(latitude, longitude)
    }

    fun selectSearchResult(result: NavigationSearchResult) {
        scope.launch {
            val resolved = resolvePlace(result.placeId, result.title) ?: return@launch
            setDestination(resolved.first, resolved.second, result.title)
        }
    }

    fun setDestination(latitude: Double, longitude: Double, name: String) {
        _state.update {
            it.copy(
                destinationLatitude = latitude,
                destinationLongitude = longitude,
                destinationName = name,
                searchResults = emptyList(),
                searchQuery = ""
            )
        }
        computeRoute()
    }

    fun clear() {
        routeJob?.cancel()
        searchJob?.cancel()
        originLat = null
        originLng = null
        totalRouteDistanceMeters = 0.0
        totalTravelTimeSeconds = 0.0
        _state.value = NavigationState()
        AppLogger.i(AppLogger.Category.UI, "Navigation cleared")
    }

    fun openInGoogleMaps() {
        val lat = _state.value.destinationLatitude ?: return
        val lng = _state.value.destinationLongitude ?: return
        val name = _state.value.destinationName ?: "Destination"
        val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val webUri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving"
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        AppLogger.i(AppLogger.Category.UI, "Opened navigation to $name")
    }

    private fun computeRoute() {
        val originLat = originLat
        val originLng = originLng
        val destLat = _state.value.destinationLatitude
        val destLng = _state.value.destinationLongitude
        if (originLat == null || originLng == null || destLat == null || destLng == null) return

        routeJob?.cancel()
        _state.update { it.copy(isRouting = true) }
        routeJob = scope.launch {
            val route = fetchDirections(originLat, originLng, destLat, destLng)
            if (route == null) {
                _state.update { it.copy(isRouting = false) }
                return@launch
            }
            totalRouteDistanceMeters = route.first
            totalTravelTimeSeconds = route.second
            _state.update {
                it.copy(
                    routeCoordinates = route.third,
                    distanceRemainingMeters = route.first,
                    etaEpochMs = if (route.second > 0) {
                        System.currentTimeMillis() + (route.second * 1000).toLong()
                    } else {
                        null
                    },
                    isRouting = false
                )
            }
            AppLogger.i(
                AppLogger.Category.UI,
                "Route computed: ${route.first.toInt()}m, ${route.second.toInt()}s, ${route.third.size} pts"
            )
        }
    }

    private suspend fun fetchAutocomplete(query: String): List<NavigationSearchResult> =
        withContext(Dispatchers.IO) {
            val key = apiKey ?: return@withContext emptyList()
            val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
            val locationBias = originLat?.let { lat ->
                originLng?.let { lng -> "&location=$lat,$lng&radius=60000" }
            }.orEmpty()
            val url =
                "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=$encodedQuery&key=$key$locationBias"
            runCatching {
                val body = httpGet(url) ?: return@runCatching emptyList()
                val json = JSONObject(body)
                if (json.optString("status") != "OK") return@runCatching emptyList()
                val predictions = json.optJSONArray("predictions") ?: return@runCatching emptyList()
                buildList {
                    for (index in 0 until predictions.length()) {
                        val item = predictions.getJSONObject(index)
                        add(
                            NavigationSearchResult(
                                placeId = item.getString("place_id"),
                                title = item.getJSONObject("structured_formatting")
                                    .getString("main_text"),
                                subtitle = item.optJSONObject("structured_formatting")
                                    ?.optString("secondary_text").orEmpty()
                            )
                        )
                    }
                }
            }.getOrElse {
                AppLogger.w(AppLogger.Category.UI, "Autocomplete failed", it)
                emptyList()
            }
        }

    private suspend fun resolvePlace(placeId: String, fallbackName: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            val key = apiKey ?: return@withContext null
            val url =
                "https://maps.googleapis.com/maps/api/place/details/json?place_id=$placeId&fields=geometry&key=$key"
            runCatching {
                val body = httpGet(url) ?: return@runCatching null
                val json = JSONObject(body)
                if (json.optString("status") != "OK") return@runCatching null
                val location = json.getJSONObject("result")
                    .getJSONObject("geometry")
                    .getJSONObject("location")
                location.getDouble("lat") to location.getDouble("lng")
            }.getOrElse {
                AppLogger.w(AppLogger.Category.UI, "Place details failed for $fallbackName", it)
                null
            }
        }

    private suspend fun fetchDirections(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): Triple<Double, Double, List<RouteCoordinate>>? = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext null
        val url =
            "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=$originLat,$originLng&destination=$destLat,$destLng&mode=driving&key=$key"
        runCatching {
            val body = httpGet(url) ?: return@runCatching null
            val json = JSONObject(body)
            if (json.optString("status") != "OK") return@runCatching null
            val route = json.getJSONArray("routes").getJSONObject(0)
            val leg = route.getJSONArray("legs").getJSONObject(0)
            val distance = leg.getJSONObject("distance").getDouble("value")
            val duration = leg.getJSONObject("duration").getDouble("value")
            val encoded = route.getJSONObject("overview_polyline").getString("points")
            val decoded = PolyUtil.decode(encoded).map { RouteCoordinate(it.latitude, it.longitude) }
            Triple(distance, duration, decoded)
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Directions failed", it)
            null
        }
    }

    private fun recomputeRemaining(latitude: Double, longitude: Double) {
        val route = _state.value.routeCoordinates
        if (route.size < 2 || totalRouteDistanceMeters <= 0) return

        var nearestIndex = 0
        var nearestDistance = Double.MAX_VALUE
        route.forEachIndexed { index, coord ->
            val distance = haversineMeters(latitude, longitude, coord.latitude, coord.longitude)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }

        var remaining = nearestDistance
        for (index in nearestIndex until route.lastIndex) {
            remaining += haversineMeters(
                route[index].latitude,
                route[index].longitude,
                route[index + 1].latitude,
                route[index + 1].longitude
            )
        }

        val etaEpochMs = if (totalRouteDistanceMeters > 0 && totalTravelTimeSeconds > 0) {
            val fraction = (remaining / totalRouteDistanceMeters).coerceIn(0.0, 1.0)
            System.currentTimeMillis() + (totalTravelTimeSeconds * fraction * 1000).toLong()
        } else {
            _state.value.etaEpochMs
        }

        _state.update {
            it.copy(distanceRemainingMeters = remaining, etaEpochMs = etaEpochMs)
        }
    }

    private fun httpGet(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
