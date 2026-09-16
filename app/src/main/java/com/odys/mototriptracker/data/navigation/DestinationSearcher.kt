package com.odys.mototriptracker.data.navigation

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.odys.mototriptracker.util.AppLogger
import com.odys.mototriptracker.util.MapsApiKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Destination / map-place lookup: Places SDK → REST → Photon → Nominatim.
 */
@Singleton
class DestinationSearcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    mapsApiKeyProvider: MapsApiKeyProvider,
    private val http: NavigationHttp,
) {
    private val apiKey = mapsApiKeyProvider.getApiKey()

    @Volatile
    private var autocompleteToken = AutocompleteSessionToken.newInstance()

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

    fun resetAutocompleteSession() {
        autocompleteToken = AutocompleteSessionToken.newInstance()
    }

    suspend fun search(
        query: String,
        biasLat: Double?,
        biasLng: Double?,
    ): List<NavigationSearchResult> {
        val textResults = fetchPlacesTextSearch(query, biasLat, biasLng)
        if (textResults.isNotEmpty()) return textResults

        val autocomplete = fetchPlacesAutocomplete(query, biasLat, biasLng)
        if (autocomplete.isNotEmpty()) return autocomplete

        val rest = fetchGoogleTextSearchRest(query, biasLat, biasLng)
        if (rest.isNotEmpty()) return rest

        AppLogger.i(AppLogger.Category.UI, "Google place search empty — Photon/Nominatim for '$query'")
        val photon = fetchPhotonSearch(query, biasLat, biasLng)
        if (photon.isNotEmpty()) return photon

        return fetchNominatimSearch(query, biasLat, biasLng)
    }

    suspend fun resolvePlace(placeId: String, fallbackName: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            if (placeId.startsWith("nominatim:") || placeId.startsWith("photon:")) {
                return@withContext null
            }
            val client = placesClient
            if (client != null) {
                val request = FetchPlaceRequest.builder(
                    placeId,
                    listOf(Place.Field.LOCATION, Place.Field.DISPLAY_NAME),
                ).setSessionToken(autocompleteToken).build()
                val fromSdk = runCatching {
                    val place = client.fetchPlace(request).awaitPlacesTask().place
                    val latLng = place.location ?: return@runCatching null
                    latLng.latitude to latLng.longitude
                }.getOrElse {
                    AppLogger.w(AppLogger.Category.UI, "Places fetchPlace failed for $fallbackName", it)
                    null
                }
                if (fromSdk != null) return@withContext fromSdk
            }

            val key = apiKey ?: return@withContext null
            val url =
                "https://maps.googleapis.com/maps/api/place/details/json?place_id=$placeId&fields=geometry&key=$key"
            runCatching {
                val body = http.get(url) ?: return@runCatching null
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

    suspend fun resolveMapPlace(
        placeId: String,
        fallbackName: String,
        latitude: Double,
        longitude: Double,
    ): PickedMapPlace = withContext(Dispatchers.IO) {
        val fallback = PickedMapPlace(
            name = fallbackName.ifBlank { "Selected place" },
            latitude = latitude,
            longitude = longitude,
            placeId = placeId,
        )
        if (placeId.isBlank()) return@withContext fallback
        val client = placesClient ?: return@withContext fallback
        val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.NATIONAL_PHONE_NUMBER,
            Place.Field.INTERNATIONAL_PHONE_NUMBER,
            Place.Field.WEBSITE_URI,
            Place.Field.LOCATION,
            Place.Field.PRIMARY_TYPE_DISPLAY_NAME,
            Place.Field.TYPES,
        )
        runCatching {
            val place = client.fetchPlace(
                FetchPlaceRequest.builder(placeId, fields).build(),
            ).awaitPlacesTask().place
            val website = place.websiteUri
            val host = website?.host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
                ?: website?.toString()?.takeIf { it.isNotBlank() }
            val category = place.primaryTypeDisplayName
                ?: place.placeTypes?.firstOrNull()
                    ?.replace('_', ' ')
                    ?.replaceFirstChar { it.titlecase() }
            PickedMapPlace(
                name = place.displayName?.takeIf { it.isNotBlank() } ?: fallback.name,
                category = category,
                address = place.formattedAddress.orEmpty(),
                phone = place.nationalPhoneNumber ?: place.internationalPhoneNumber,
                websiteHost = host,
                websiteUrl = website?.toString(),
                latitude = place.location?.latitude ?: latitude,
                longitude = place.location?.longitude ?: longitude,
                placeId = place.id ?: placeId,
            )
        }.onFailure {
            AppLogger.w(AppLogger.Category.UI, "Map place details failed for $placeId", it)
        }.getOrDefault(fallback)
    }

    private suspend fun fetchPlacesTextSearch(
        query: String,
        biasLat: Double?,
        biasLng: Double?,
    ): List<NavigationSearchResult> = withContext(Dispatchers.IO) {
        val client = placesClient ?: return@withContext emptyList()
        val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION,
        )
        val builder = SearchByTextRequest.builder(query, fields)
            .setMaxResultCount(10)
        if (biasLat != null && biasLng != null) {
            builder.setLocationBias(CircularBounds.newInstance(LatLng(biasLat, biasLng), 50_000.0))
        }
        runCatching {
            val response = client.searchByText(builder.build()).awaitPlacesTask()
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
                    longitude = location?.longitude,
                )
            }
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Places text search failed", it)
            emptyList()
        }
    }

    private suspend fun fetchPlacesAutocomplete(
        query: String,
        biasLat: Double?,
        biasLng: Double?,
    ): List<NavigationSearchResult> = withContext(Dispatchers.IO) {
        val client = placesClient ?: return@withContext emptyList()
        val requestBuilder = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(autocompleteToken)
            .setQuery(query)
        if (biasLat != null && biasLng != null) {
            requestBuilder.setOrigin(LatLng(biasLat, biasLng))
            requestBuilder.setLocationBias(
                CircularBounds.newInstance(LatLng(biasLat, biasLng), 50_000.0),
            )
        }
        runCatching {
            val response = client.findAutocompletePredictions(requestBuilder.build()).awaitPlacesTask()
            response.autocompletePredictions.map { prediction ->
                NavigationSearchResult(
                    placeId = prediction.placeId,
                    title = prediction.getPrimaryText(null).toString(),
                    subtitle = prediction.getSecondaryText(null).toString(),
                )
            }
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Places autocomplete failed", it)
            emptyList()
        }
    }

    private suspend fun fetchGoogleTextSearchRest(
        query: String,
        biasLat: Double?,
        biasLng: Double?,
    ): List<NavigationSearchResult> = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext emptyList()
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val locationBias = if (biasLat != null && biasLng != null) {
            "&location=$biasLat,$biasLng&radius=50000"
        } else {
            ""
        }
        val url =
            "https://maps.googleapis.com/maps/api/place/textsearch/json?" +
                "query=$encoded&key=$key$locationBias"
        runCatching {
            val body = http.get(url) ?: return@runCatching emptyList()
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
                            longitude = location.getDouble("lng"),
                        ),
                    )
                }
            }
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Places Text Search REST failed", it)
            emptyList()
        }
    }

    private suspend fun fetchPhotonSearch(
        query: String,
        biasLat: Double?,
        biasLng: Double?,
    ): List<NavigationSearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val bias = if (biasLat != null && biasLng != null) "&lat=$biasLat&lon=$biasLng" else ""
        val url = "https://photon.komoot.io/api/?q=$encoded&limit=10$bias"
        runCatching {
            val body = http.get(
                url,
                userAgent = "MotoTripTracker/1.0 (Android; destination search)",
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
                        props.optString("country").takeIf { it.isNotBlank() },
                    ).joinToString(", ").ifBlank { props.optString("type") }
                    add(
                        NavigationSearchResult(
                            placeId = "photon:${props.optString("osm_type")}:${props.optLong("osm_id")}",
                            title = name,
                            subtitle = address,
                            latitude = coords.getDouble(1),
                            longitude = coords.getDouble(0),
                        ),
                    )
                }
            }
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Photon search failed", it)
            emptyList()
        }
    }

    private suspend fun fetchNominatimSearch(
        query: String,
        biasLat: Double?,
        biasLng: Double?,
    ): List<NavigationSearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val bias = if (biasLat != null && biasLng != null) {
            "&viewbox=${biasLng - 1.5},${biasLat + 1.5},${biasLng + 1.5},${biasLat - 1.5}&bounded=0"
        } else {
            ""
        }
        val url =
            "https://nominatim.openstreetmap.org/search?q=$encoded&format=jsonv2" +
                "&addressdetails=1&limit=10$bias"
        runCatching {
            val body = http.get(
                url,
                userAgent = "MotoTripTracker/1.0 (Android; destination search)",
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
                            longitude = item.getDouble("lon"),
                        ),
                    )
                }
            }
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Nominatim search failed", it)
            emptyList()
        }
    }
}
