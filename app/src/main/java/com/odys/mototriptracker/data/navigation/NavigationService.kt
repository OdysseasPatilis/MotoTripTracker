package com.odys.mototriptracker.data.navigation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.net.toUri
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.maps.android.PolyUtil
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.util.AppLogger
import com.odys.mototriptracker.util.MapsApiKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class NavigationService @Inject constructor(
    @ApplicationContext context: Context,
    mapsApiKeyProvider: MapsApiKeyProvider,
    private val voice: NavigationVoicePrompt
) {
    private val context = context
    private val apiKey = mapsApiKeyProvider.getApiKey()
    private val httpClient = OkHttpClient.Builder()
        .dns(Ipv4PreferringDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(NavigationState(isVoiceEnabled = voice.isEnabled))
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    var onRouteApplied: ((List<RouteCoordinate>, Double) -> Unit)? = null
    var onRouteCleared: (() -> Unit)? = null

    private var originLat: Double? = null
    private var originLng: Double? = null
    private var totalRouteDistanceMeters = 0.0
    private var totalTravelTimeSeconds = 0.0
    private var nearestRouteDistanceMeters = 0.0
    private var lastRecalculateAtMs = 0L
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var autocompleteToken = AutocompleteSessionToken.newInstance()
    private var approachedStepId: String? = null
    private var announcedStepId: String? = null

    private val placesClient: PlacesClient? by lazy {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: return@lazy null
        runCatching {
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(context.applicationContext, key)
            }
            Places.createClient(context.applicationContext)
        }.onFailure {
            AppLogger.w(AppLogger.Category.UI, "Places SDK init failed", it)
        }.getOrNull()
    }

    fun updateSearchQuery(query: String) {
        if (query == _state.value.searchQuery) return
        _state.update {
            it.copy(
                searchQuery = query,
                searchError = null,
                isSearching = query.isNotBlank(),
                searchResults = if (query.isBlank()) emptyList() else it.searchResults
            )
        }
        if (query.isBlank()) {
            searchJob?.cancel()
            _state.update { it.copy(isSearching = false, searchResults = emptyList()) }
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(isSearching = true, searchError = null) }
            val results = searchDestinations(query.trim())
            if (_state.value.searchQuery.trim() != query.trim()) return@launch
            _state.update {
                it.copy(
                    searchResults = results,
                    isSearching = false,
                    searchError = if (results.isEmpty()) {
                        "No places found. Try the workplace name plus city, e.g. \"Acme Athens\"."
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun updateOrigin(latitude: Double, longitude: Double) {
        originLat = latitude
        originLng = longitude
        if (_state.value.hasDestination && !_state.value.hasRoute && !_state.value.isRouting) {
            computeRoute(isRecalculation = false)
        }
        if (_state.value.hasRoute) {
            recomputeRemaining(latitude, longitude)
            advanceStepIfNeeded(latitude, longitude)
            checkOffRouteAndRecalculate(latitude, longitude)
        }
    }

    fun selectSearchResult(result: NavigationSearchResult) {
        scope.launch {
            val latLng = when {
                result.latitude != null && result.longitude != null ->
                    result.latitude to result.longitude
                result.placeId.isNotBlank() ->
                    resolvePlace(result.placeId, result.title)
                else -> null
            }
            if (latLng == null) {
                AppLogger.w(AppLogger.Category.UI, "Could not resolve place: ${result.title}")
                _state.update { it.copy(searchError = "Couldn't open that place. Try another result.") }
                return@launch
            }
            autocompleteToken = AutocompleteSessionToken.newInstance()
            val label = if (result.subtitle.isNotBlank()) {
                "${result.title} · ${result.subtitle}"
            } else {
                result.title
            }
            setDestination(latLng.first, latLng.second, label)
        }
    }

    fun setDestination(latitude: Double, longitude: Double, name: String) {
        _state.update {
            it.copy(
                destinationLatitude = latitude,
                destinationLongitude = longitude,
                destinationName = name,
                searchResults = emptyList(),
                searchQuery = "",
                isSearching = false,
                searchError = null
            )
        }
        computeRoute(isRecalculation = false)
    }

    fun clear() {
        routeJob?.cancel()
        searchJob?.cancel()
        // Keep origin so the next destination can route immediately.
        totalRouteDistanceMeters = 0.0
        totalTravelTimeSeconds = 0.0
        nearestRouteDistanceMeters = 0.0
        approachedStepId = null
        announcedStepId = null
        voice.stop()
        val voiceEnabled = _state.value.isVoiceEnabled
        _state.value = NavigationState(isVoiceEnabled = voiceEnabled)
        onRouteCleared?.invoke()
        AppLogger.i(AppLogger.Category.UI, "Navigation cleared")
    }

    fun toggleVoice() {
        val enabled = !_state.value.isVoiceEnabled
        voice.isEnabled = enabled
        _state.update { it.copy(isVoiceEnabled = enabled) }
        if (!enabled) voice.stop()
        AppLogger.i(AppLogger.Category.UI, "Navigation voice ${if (enabled) "on" else "off"}")
    }

    fun navigateToNearestPetrol(
        fallbackLat: Double? = null,
        fallbackLng: Double? = null,
        onResult: ((PetrolSearchOutcome) -> Unit)? = null
    ) {
        val lat = originLat ?: fallbackLat
        val lng = originLng ?: fallbackLng
        if (lat == null || lng == null) {
            onResult?.invoke(PetrolSearchOutcome.NONE_NEARBY)
            return
        }
        if (originLat == null) {
            originLat = lat
            originLng = lng
        }
        scope.launch {
            val outcome = findNearestPetrol(lat, lng)
            onResult?.invoke(outcome)
        }
    }

    fun openInGoogleMaps() {
        val lat = _state.value.destinationLatitude ?: return
        val lng = _state.value.destinationLongitude ?: return
        val uri = "google.navigation:q=$lat,$lng&mode=d".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun computeRoute(isRecalculation: Boolean) {
        val oLat = originLat
        val oLng = originLng
        val destLat = _state.value.destinationLatitude
        val destLng = _state.value.destinationLongitude
        if (oLat == null || oLng == null || destLat == null || destLng == null) {
            AppLogger.w(
                AppLogger.Category.UI,
                "Cannot route — origin=${oLat != null} dest=${destLat != null}"
            )
            return
        }

        routeJob?.cancel()
        _state.update {
            it.copy(
                isRouting = !isRecalculation,
                isRecalculating = isRecalculation
            )
        }
        routeJob = scope.launch {
            val route = fetchDirections(oLat, oLng, destLat, destLng)
                ?: fetchOsrmDirections(oLat, oLng, destLat, destLng)
            if (route == null) {
                AppLogger.w(AppLogger.Category.UI, "All routing providers failed")
                _state.update { it.copy(isRouting = false, isRecalculating = false) }
                return@launch
            }
            applyRoute(route, isRecalculation)
        }
    }

    private fun applyRoute(route: DirectionsResult, isRecalculation: Boolean) {
        totalRouteDistanceMeters = route.distanceMeters
        totalTravelTimeSeconds = route.travelTimeSeconds
        nearestRouteDistanceMeters = 0.0
        approachedStepId = null
        announcedStepId = null
        voice.stop()
        if (isRecalculation) lastRecalculateAtMs = System.currentTimeMillis()

        _state.update {
            it.copy(
                routeCoordinates = route.coordinates,
                distanceRemainingMeters = route.distanceMeters,
                etaEpochMs = if (route.travelTimeSeconds > 0) {
                    System.currentTimeMillis() + (route.travelTimeSeconds * 1000).toLong()
                } else {
                    null
                },
                steps = route.steps,
                currentStepIndex = 0,
                distanceToNextManeuverMeters = route.steps.firstOrNull()?.distanceMeters ?: route.distanceMeters,
                isRouting = false,
                isRecalculating = false,
                isOffRoute = false
            )
        }
        onRouteApplied?.invoke(route.coordinates, route.travelTimeSeconds)
        AppLogger.i(
            AppLogger.Category.UI,
            "Route ${if (isRecalculation) "recalculated" else "computed"}: " +
                "${route.distanceMeters.toInt()}m, ${route.steps.size} steps"
        )
    }

    private suspend fun findNearestPetrol(lat: Double, lng: Double): PetrolSearchOutcome =
        withContext(Dispatchers.IO) {
            val stations = fetchOsmFuelStations(lat, lng)
            if (stations.isEmpty()) {
                return@withContext findPetrolViaPlaces(lat, lng)
            }
            val nearest = stations.minByOrNull { it.distanceMeters } ?: return@withContext PetrolSearchOutcome.NONE_NEARBY
            withContext(Dispatchers.Main) {
                setDestination(nearest.latitude, nearest.longitude, nearest.name)
            }
            PetrolSearchOutcome.FOUND
        }

    private suspend fun findPetrolViaPlaces(lat: Double, lng: Double): PetrolSearchOutcome {
        val key = apiKey ?: return PetrolSearchOutcome.NONE_NEARBY
        val nearbyUrl =
            "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                "location=$lat,$lng&radius=15000&type=gas_station&key=$key"
        val textUrl =
            "https://maps.googleapis.com/maps/api/place/textsearch/json?" +
                "query=${URLEncoder.encode("gas station", Charsets.UTF_8.name())}" +
                "&location=$lat,$lng&radius=15000&key=$key"

        for (url in listOf(nearbyUrl, textUrl)) {
            val outcome = runCatching {
                val body = httpGet(url) ?: return@runCatching null
                val json = JSONObject(body)
                val status = json.optString("status")
                if (status != "OK" && status != "ZERO_RESULTS") {
                    AppLogger.w(AppLogger.Category.UI, "Petrol Places status=$status")
                    return@runCatching null
                }
                val results = json.optJSONArray("results") ?: return@runCatching null
                if (results.length() == 0) return@runCatching null
                val item = results.getJSONObject(0)
                val location = item.getJSONObject("geometry").getJSONObject("location")
                val name = item.optString("name", "Petrol station")
                Triple(location.getDouble("lat"), location.getDouble("lng"), name)
            }.getOrNull() ?: continue

            withContext(Dispatchers.Main) {
                setDestination(outcome.first, outcome.second, outcome.third)
            }
            return PetrolSearchOutcome.FOUND
        }
        return PetrolSearchOutcome.NONE_NEARBY
    }

    private suspend fun fetchOsmFuelStations(lat: Double, lng: Double): List<PetrolCandidate> =
        withContext(Dispatchers.IO) {
            val query = """
                [out:json][timeout:20];
                (
                  node["amenity"="fuel"](around:15000,$lat,$lng);
                  way["amenity"="fuel"](around:15000,$lat,$lng);
                );
                out center tags;
            """.trimIndent()

            for (endpoint in OVERPASS_ENDPOINTS) {
                val body = FormBody.Builder().add("data", query).build()
                val request = Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                val stations = runCatching {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            AppLogger.w(AppLogger.Category.UI, "Petrol Overpass HTTP ${response.code} from $endpoint")
                            return@runCatching emptyList()
                        }
                        val json = JSONObject(response.body?.string().orEmpty())
                        val elements = json.optJSONArray("elements") ?: return@runCatching emptyList()
                        buildList {
                            for (i in 0 until elements.length()) {
                                val el = elements.getJSONObject(i)
                                val stationLat = el.optDouble("lat", Double.NaN).takeIf { !it.isNaN() }
                                    ?: el.optJSONObject("center")?.optDouble("lat") ?: continue
                                val stationLng = el.optDouble("lon", Double.NaN).takeIf { !it.isNaN() }
                                    ?: el.optJSONObject("center")?.optDouble("lon") ?: continue
                                val tags = el.optJSONObject("tags")
                                val brand = tags?.optString("brand").orEmpty().takeIf { it.isNotBlank() }
                                val nameTag = tags?.optString("name").orEmpty().takeIf { it.isNotBlank() }
                                val name = when {
                                    brand != null && nameTag != null && brand != nameTag -> "$brand · $nameTag"
                                    nameTag != null -> nameTag
                                    brand != null -> brand
                                    else -> "Petrol station"
                                }
                                add(
                                    PetrolCandidate(
                                        name = name,
                                        latitude = stationLat,
                                        longitude = stationLng,
                                        distanceMeters = haversineMeters(lat, lng, stationLat, stationLng)
                                    )
                                )
                            }
                        }
                    }
                }.getOrElse {
                    AppLogger.w(AppLogger.Category.UI, "Petrol Overpass $endpoint failed", it)
                    emptyList()
                }
                if (stations.isNotEmpty()) return@withContext stations
            }
            emptyList()
        }

    private suspend fun searchDestinations(query: String): List<NavigationSearchResult> {
        // 1) Google Text Search — best for business / workplace names ("my company", cafes, etc.)
        val textResults = fetchPlacesTextSearch(query)
        if (textResults.isNotEmpty()) return textResults

        // 2) Autocomplete — good for street addresses / partial place names
        val autocomplete = fetchPlacesAutocomplete(query)
        if (autocomplete.isNotEmpty()) return autocomplete

        // 3) Legacy Places Text Search REST (works if Places API is enabled)
        val rest = fetchGoogleTextSearchRest(query)
        if (rest.isNotEmpty()) return rest

        // 4) Photon (OSM) — much better than Nominatim for named POIs
        AppLogger.i(AppLogger.Category.UI, "Google place search empty — Photon/Nominatim for '$query'")
        val photon = fetchPhotonSearch(query)
        if (photon.isNotEmpty()) return photon

        return fetchNominatimSearch(query)
    }

    /** Places SDK Text Search (New) — finds businesses by name and returns address + coordinates. */
    private suspend fun fetchPlacesTextSearch(query: String): List<NavigationSearchResult> =
        withContext(Dispatchers.IO) {
            val client = placesClient ?: return@withContext emptyList()
            val fields = listOf(
                Place.Field.ID,
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.LOCATION
            )
            val builder = SearchByTextRequest.builder(query, fields)
                .setMaxResultCount(10)
            originLat?.let { lat ->
                originLng?.let { lng ->
                    builder.setLocationBias(CircularBounds.newInstance(LatLng(lat, lng), 50_000.0))
                }
            }
            runCatching {
                val response = client.searchByText(builder.build()).awaitTask()
                response.places.mapNotNull { place ->
                    val id = place.id ?: return@mapNotNull null
                    val name = place.displayName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val address = place.formattedAddress.orEmpty()
                    val location = place.location
                    NavigationSearchResult(
                        placeId = id,
                        title = name,
                        subtitle = address,
                        latitude = location?.latitude,
                        longitude = location?.longitude
                    )
                }
            }.getOrElse {
                AppLogger.w(AppLogger.Category.UI, "Places text search failed", it)
                emptyList()
            }
        }

    private suspend fun fetchPlacesAutocomplete(query: String): List<NavigationSearchResult> =
        withContext(Dispatchers.IO) {
            val client = placesClient ?: return@withContext emptyList()
            val requestBuilder = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(autocompleteToken)
                .setQuery(query)
            originLat?.let { lat ->
                originLng?.let { lng ->
                    requestBuilder.setOrigin(LatLng(lat, lng))
                    requestBuilder.setLocationBias(
                        CircularBounds.newInstance(LatLng(lat, lng), 50_000.0)
                    )
                }
            }
            runCatching {
                val response = client.findAutocompletePredictions(requestBuilder.build()).awaitTask()
                response.autocompletePredictions.map { prediction ->
                    NavigationSearchResult(
                        placeId = prediction.placeId,
                        title = prediction.getPrimaryText(null).toString(),
                        subtitle = prediction.getSecondaryText(null).toString()
                    )
                }
            }.getOrElse {
                AppLogger.w(AppLogger.Category.UI, "Places autocomplete failed", it)
                emptyList()
            }
        }

    private suspend fun fetchGoogleTextSearchRest(query: String): List<NavigationSearchResult> =
        withContext(Dispatchers.IO) {
            val key = apiKey ?: return@withContext emptyList()
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val locationBias = originLat?.let { lat ->
                originLng?.let { lng -> "&location=$lat,$lng&radius=50000" }
            }.orEmpty()
            val url =
                "https://maps.googleapis.com/maps/api/place/textsearch/json?" +
                    "query=$encoded&key=$key$locationBias"
            runCatching {
                val body = httpGet(url) ?: return@runCatching emptyList()
                val json = JSONObject(body)
                val status = json.optString("status")
                if (status != "OK" && status != "ZERO_RESULTS") {
                    AppLogger.w(AppLogger.Category.UI, "Places Text Search REST status=$status")
                    return@runCatching emptyList()
                }
                val results = json.optJSONArray("results") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val location = item.getJSONObject("geometry").getJSONObject("location")
                        add(
                            NavigationSearchResult(
                                placeId = item.optString("place_id"),
                                title = item.optString("name"),
                                subtitle = item.optString("formatted_address"),
                                latitude = location.getDouble("lat"),
                                longitude = location.getDouble("lng")
                            )
                        )
                    }
                }
            }.getOrElse {
                AppLogger.w(AppLogger.Category.UI, "Places Text Search REST failed", it)
                emptyList()
            }
        }

    /** Photon (Komoot) — strong open-data POI / business-name search. */
    private suspend fun fetchPhotonSearch(query: String): List<NavigationSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val bias = originLat?.let { lat ->
                originLng?.let { lng -> "&lat=$lat&lon=$lng" }
            }.orEmpty()
            val url = "https://photon.komoot.io/api/?q=$encoded&limit=10$bias"
            runCatching {
                val body = httpGet(
                    url,
                    userAgent = "MotoTripTracker/1.0 (Android; destination search)"
                ) ?: return@runCatching emptyList()
                val features = JSONObject(body).optJSONArray("features") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val props = feature.getJSONObject("properties")
                        val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
                        val name = props.optString("name").ifBlank {
                            props.optString("street").ifBlank { props.optString("city") }
                        }
                        if (name.isBlank()) continue
                        val street = props.optString("street").takeIf { it.isNotBlank() }
                        val number = props.optString("housenumber").takeIf { it.isNotBlank() }
                        val address = listOfNotNull(
                            when {
                                street != null && number != null -> "$street $number"
                                street != null -> street
                                else -> null
                            },
                            props.optString("postcode").takeIf { it.isNotBlank() },
                            props.optString("city").takeIf { it.isNotBlank() },
                            props.optString("state").takeIf { it.isNotBlank() },
                            props.optString("country").takeIf { it.isNotBlank() }
                        ).joinToString(", ").ifBlank { props.optString("type") }
                        add(
                            NavigationSearchResult(
                                placeId = "photon:${props.optString("osm_type")}:${props.optLong("osm_id")}",
                                title = name,
                                subtitle = address,
                                latitude = coords.getDouble(1),
                                longitude = coords.getDouble(0)
                            )
                        )
                    }
                }
            }.getOrElse {
                AppLogger.w(AppLogger.Category.UI, "Photon search failed", it)
                emptyList()
            }
        }

    private suspend fun fetchNominatimSearch(query: String): List<NavigationSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val bias = originLat?.let { lat ->
                originLng?.let { lng ->
                    "&viewbox=${lng - 1.5},${lat + 1.5},${lng + 1.5},${lat - 1.5}&bounded=0"
                }
            }.orEmpty()
            val url =
                "https://nominatim.openstreetmap.org/search?q=$encoded&format=jsonv2" +
                    "&addressdetails=1&limit=10$bias"
            runCatching {
                val body = httpGet(
                    url,
                    userAgent = "MotoTripTracker/1.0 (Android; destination search)"
                ) ?: return@runCatching emptyList()
                val array = org.json.JSONArray(body)
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val display = item.optString("display_name")
                        val name = item.optString("name").ifBlank {
                            display.substringBefore(',').ifBlank { display }
                        }
                        val subtitle = display
                            .removePrefix(name)
                            .trimStart(',', ' ')
                            .ifBlank { item.optString("type") }
                            .take(100)
                        add(
                            NavigationSearchResult(
                                placeId = "nominatim:${item.optString("place_id")}",
                                title = name,
                                subtitle = subtitle,
                                latitude = item.getDouble("lat"),
                                longitude = item.getDouble("lon")
                            )
                        )
                    }
                }
            }.getOrElse {
                AppLogger.w(AppLogger.Category.UI, "Nominatim search failed", it)
                emptyList()
            }
        }

    private suspend fun resolvePlace(placeId: String, fallbackName: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            if (placeId.startsWith("nominatim:") || placeId.startsWith("photon:")) return@withContext null
            val client = placesClient
            if (client != null) {
                val request = FetchPlaceRequest.builder(
                    placeId,
                    listOf(Place.Field.LOCATION, Place.Field.DISPLAY_NAME)
                ).setSessionToken(autocompleteToken).build()
                val fromSdk = runCatching {
                    val place = client.fetchPlace(request).awaitTask().place
                    val latLng = place.location ?: return@runCatching null
                    latLng.latitude to latLng.longitude
                }.getOrElse {
                    AppLogger.w(AppLogger.Category.UI, "Places fetchPlace failed for $fallbackName", it)
                    null
                }
                if (fromSdk != null) return@withContext fromSdk
            }

            // Legacy Place Details REST fallback (often blocked by Android-restricted keys).
            val key = apiKey ?: return@withContext null
            val url =
                "https://maps.googleapis.com/maps/api/place/details/json?place_id=$placeId&fields=geometry&key=$key"
            runCatching {
                val body = httpGet(url) ?: return@runCatching null
                val json = JSONObject(body)
                val status = json.optString("status")
                if (status != "OK") {
                    AppLogger.w(AppLogger.Category.UI, "Place details status=$status")
                    return@runCatching null
                }
                val location = json.getJSONObject("result").getJSONObject("geometry").getJSONObject("location")
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
    ): DirectionsResult? = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext null
        val url =
            "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=$originLat,$originLng&destination=$destLat,$destLng&mode=driving&key=$key"
        runCatching {
            val body = httpGet(url) ?: return@runCatching null
            val json = JSONObject(body)
            val status = json.optString("status")
            if (status != "OK") {
                AppLogger.w(AppLogger.Category.UI, "Directions status=$status")
                return@runCatching null
            }
            val route = json.getJSONArray("routes").getJSONObject(0)
            val leg = route.getJSONArray("legs").getJSONObject(0)
            val distance = leg.getJSONObject("distance").getDouble("value")
            val duration = leg.getJSONObject("duration").getDouble("value")
            val encoded = route.getJSONObject("overview_polyline").getString("points")
            val coordinates = PolyUtil.decode(encoded).map { RouteCoordinate(it.latitude, it.longitude) }

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
                            endLongitude = end.getDouble("lng")
                        )
                    )
                }
            }
            DirectionsResult(distance, duration, coordinates, steps)
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Directions failed", it)
            null
        }
    }

    /** Public OSRM fallback when Google Directions is denied / unavailable. */
    private suspend fun fetchOsrmDirections(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): DirectionsResult? = withContext(Dispatchers.IO) {
        val url =
            "https://router.project-osrm.org/route/v1/driving/" +
                "$originLng,$originLat;$destLng,$destLat" +
                "?overview=full&geometries=polyline&steps=true"
        runCatching {
            val body = httpGet(url, userAgent = "MotoTripTracker/1.0") ?: return@runCatching null
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") {
                AppLogger.w(AppLogger.Category.UI, "OSRM code=${json.optString("code")}")
                return@runCatching null
            }
            val route = json.getJSONArray("routes").getJSONObject(0)
            val distance = route.getDouble("distance")
            val duration = route.getDouble("duration")
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
                                endLongitude = location.getDouble(0)
                            )
                        )
                    }
                }
            }
            DirectionsResult(distance, duration, coordinates, steps)
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
            "roundabout", "rotary" -> if (road != null) "Enter the roundabout toward $road" else "Enter the roundabout"
            else -> road?.let { "Continue on $it" }.orEmpty()
        }
    }

    private fun recomputeRemaining(latitude: Double, longitude: Double) {
        val route = _state.value.routeCoordinates
        if (route.size < 2) return

        var nearestIndex = 0
        var nearestDistance = Double.MAX_VALUE
        route.forEachIndexed { index, coord ->
            val distance = haversineMeters(latitude, longitude, coord.latitude, coord.longitude)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }
        nearestRouteDistanceMeters = nearestDistance

        var remaining = nearestDistance
        for (index in nearestIndex until route.lastIndex) {
            remaining += haversineMeters(
                route[index].latitude, route[index].longitude,
                route[index + 1].latitude, route[index + 1].longitude
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

    private fun advanceStepIfNeeded(latitude: Double, longitude: Double) {
        val steps = _state.value.steps
        if (steps.isEmpty()) {
            _state.update { it.copy(distanceToNextManeuverMeters = it.distanceRemainingMeters) }
            return
        }

        val current = steps.getOrNull(_state.value.currentStepIndex)
        if (current != null) {
            val toEnd = haversineMeters(
                latitude, longitude,
                current.endLatitude, current.endLongitude
            )
            _state.update { it.copy(distanceToNextManeuverMeters = toEnd) }
            maybeAnnounceApproach(current, toEnd)
        }

        var index = _state.value.currentStepIndex
        while (index < steps.size) {
            val candidate = steps[index]
            val distance = haversineMeters(
                latitude, longitude,
                candidate.endLatitude, candidate.endLongitude
            )
            if (distance <= STEP_ADVANCE_METERS && index < steps.lastIndex) {
                index++
                continue
            }
            break
        }

        if (index != _state.value.currentStepIndex) {
            approachedStepId = null
            val next = steps.getOrNull(index)
            val distanceToManeuver = next?.let {
                haversineMeters(latitude, longitude, it.endLatitude, it.endLongitude)
            } ?: _state.value.distanceRemainingMeters
            _state.update {
                it.copy(currentStepIndex = index, distanceToNextManeuverMeters = distanceToManeuver)
            }
            if (next != null) {
                AppLogger.i(
                    AppLogger.Category.UI,
                    "Advanced to step ${index + 1}/${steps.size}: ${next.instruction}"
                )
                announceStep(next)
            }
            lightHaptic()
        }
    }

    private fun maybeAnnounceApproach(step: NavStep, distanceMeters: Double) {
        if (distanceMeters > APPROACH_ANNOUNCE_METERS) return
        if (approachedStepId == step.id) return
        approachedStepId = step.id
        val distance = NavigationState.formatDistance(distanceMeters)
        voice.speak("In $distance, ${step.instruction}")
    }

    private fun announceStep(step: NavStep) {
        if (announcedStepId == step.id) return
        announcedStepId = step.id
        voice.speak(step.instruction)
    }

    private fun lightHaptic() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25)
            }
        }
    }

    private fun checkOffRouteAndRecalculate(latitude: Double, longitude: Double) {
        val state = _state.value
        if (!state.hasDestination || !state.hasRoute || state.isRouting || state.isRecalculating) return

        if (nearestRouteDistanceMeters > OFF_ROUTE_THRESHOLD_METERS) {
            _state.update { it.copy(isOffRoute = true) }
            val now = System.currentTimeMillis()
            if (now - lastRecalculateAtMs >= RECALCULATE_COOLDOWN_MS) {
                computeRoute(isRecalculation = true)
            }
        } else if (state.isOffRoute && nearestRouteDistanceMeters <= OFF_ROUTE_THRESHOLD_METERS / 2.0) {
            _state.update { it.copy(isOffRoute = false) }
        }
    }

    private fun httpGet(url: String, userAgent: String? = null): String? {
        val request = Request.Builder().url(url).get().apply {
            if (userAgent != null) header("User-Agent", userAgent)
        }.build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result -> cont.resume(result) }
            addOnFailureListener { error -> cont.resumeWith(Result.failure(error)) }
            addOnCanceledListener { cont.cancel() }
        }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class DirectionsResult(
        val distanceMeters: Double,
        val travelTimeSeconds: Double,
        val coordinates: List<RouteCoordinate>,
        val steps: List<NavStep>
    )

    private data class PetrolCandidate(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val distanceMeters: Double
    )

    companion object {
        private const val OFF_ROUTE_THRESHOLD_METERS = 80.0
        private const val STEP_ADVANCE_METERS = 35.0
        private const val APPROACH_ANNOUNCE_METERS = 250.0
        private const val RECALCULATE_COOLDOWN_MS = 12_000L
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val USER_AGENT = "MotoTripTracker/1.0 (Android; motorcycle trip tracker)"

        private val OVERPASS_ENDPOINTS = listOf(
            "https://lz4.overpass-api.de/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass-api.de/api/interpreter"
        )
    }

    private object Ipv4PreferringDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val ipv4 = all.filterIsInstance<Inet4Address>()
            return if (ipv4.isNotEmpty()) ipv4 else all
        }
    }
}
