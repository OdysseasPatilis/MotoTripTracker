package com.odys.mototriptracker.data.petrol

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.IsOpenRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.odys.mototriptracker.util.AppLogger
import com.odys.mototriptracker.util.MapsApiKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GooglePetrolPlace(
    val placeId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val isOpenNow: Boolean?,
    val weekdayHours: List<String> = emptyList(),
    val rating: Double?,
    val ratingCount: Int?
)

data class GoogleOpenInfo(
    val isOpenNow: Boolean?,
    val weekdayHours: List<String>
)

data class GooglePetrolDetails(
    val placeId: String,
    val name: String?,
    val address: String?,
    val phone: String?,
    val rating: Double?,
    val ratingCount: Int?,
    val websiteUri: String?,
    val googleMapsUri: String?,
    val isOpenNow: Boolean?,
    val weekdayHours: List<String>,
    val businessStatus: String?,
    val photoBitmap: android.graphics.Bitmap? = null,
    val mapPreviewBitmap: android.graphics.Bitmap? = null
)

/** Google Places enrichment for petrol stations (hours, address, phone, rating). */
@Singleton
class PetrolPlacesEnricher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    mapsApiKeyProvider: MapsApiKeyProvider
) {
    private val apiKey = mapsApiKeyProvider.getApiKey()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val placesClient: PlacesClient? by lazy {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: return@lazy null
        runCatching {
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(context.applicationContext, key)
            }
            Places.createClient(context.applicationContext)
        }.onFailure {
            AppLogger.w(AppLogger.Category.UI, "Petrol Places init failed", it)
        }.getOrNull()
    }

    suspend fun findNearbyGasStations(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): List<GooglePetrolPlace> = withContext(Dispatchers.IO) {
        val client = placesClient ?: return@withContext emptyList()
        val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.LOCATION,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.CURRENT_OPENING_HOURS,
            Place.Field.OPENING_HOURS,
            Place.Field.RATING,
            Place.Field.USER_RATING_COUNT
        )
        val restriction = CircularBounds.newInstance(
            LatLng(latitude, longitude),
            radiusMeters.coerceIn(500, 50_000).toDouble()
        )
        val request = SearchNearbyRequest.builder(restriction, fields)
            .setIncludedTypes(listOf("gas_station"))
            .setMaxResultCount(20)
            .build()

        runCatching {
            val response = client.searchNearby(request).awaitTask()
            response.places.mapNotNull { place ->
                val id = place.id ?: return@mapNotNull null
                val location = place.location ?: return@mapNotNull null
                val hours = place.currentOpeningHours?.weekdayText
                    ?: place.openingHours?.weekdayText
                    ?: emptyList()
                GooglePetrolPlace(
                    placeId = id,
                    name = place.displayName?.takeIf { it.isNotBlank() } ?: "Petrol station",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = place.formattedAddress,
                    isOpenNow = null, // nearby isOpen is unreliable without UTC_OFFSET — resolved later
                    weekdayHours = hours,
                    rating = place.rating?.toDouble(),
                    ratingCount = place.userRatingCount
                )
            }
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Petrol Places nearby search failed", it)
            emptyList()
        }
    }

    /**
     * Resolves open-now via [PlacesClient.isOpen] (requires UTC offset internally) and weekday text.
     * Nearby search alone often cannot compute open status — this fills the list badges.
     */
    suspend fun fetchOpenInfo(placeIds: List<String>): Map<String, GoogleOpenInfo> =
        withContext(Dispatchers.IO) {
            val client = placesClient ?: return@withContext emptyMap()
            val unique = placeIds.distinct().take(MAX_OPEN_LOOKUPS)
            if (unique.isEmpty()) return@withContext emptyMap()

            coroutineScope {
                unique.map { placeId ->
                    async {
                        placeId to resolveOpenInfo(client, placeId)
                    }
                }.awaitAll()
                    .mapNotNull { (id, info) -> info?.let { id to it } }
                    .toMap()
            }
        }

    private suspend fun resolveOpenInfo(client: PlacesClient, placeId: String): GoogleOpenInfo? {
        return runCatching {
            val isOpenTask = client.isOpen(IsOpenRequest.newInstance(placeId)).awaitTask()
            val isOpenNow = isOpenTask.isOpen

            val detailsRequest = FetchPlaceRequest.builder(
                placeId,
                listOf(
                    Place.Field.ID,
                    Place.Field.CURRENT_OPENING_HOURS,
                    Place.Field.OPENING_HOURS,
                    Place.Field.UTC_OFFSET,
                    Place.Field.BUSINESS_STATUS
                )
            ).build()
            val place = client.fetchPlace(detailsRequest).awaitTask().place
            val hours = place.currentOpeningHours?.weekdayText
                ?: place.openingHours?.weekdayText
                ?: emptyList()

            val resolvedOpen = isOpenNow
                ?: place.isOpen
                ?: when (GoogleWeekdayHoursParser.statusNow(hours)) {
                    OpeningHoursEvaluator.Status.OPEN -> true
                    OpeningHoursEvaluator.Status.CLOSED -> false
                    OpeningHoursEvaluator.Status.UNKNOWN -> null
                }

            GoogleOpenInfo(isOpenNow = resolvedOpen, weekdayHours = hours)
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Petrol open-info failed for $placeId", it)
            null
        }
    }

    suspend fun fetchDetails(
        placeId: String?,
        latitude: Double,
        longitude: Double
    ): GooglePetrolDetails? = withContext(Dispatchers.IO) {
        val mapPreview = fetchStaticMapPreview(latitude, longitude)
        val client = placesClient
        if (placeId.isNullOrBlank() || client == null) {
            return@withContext GooglePetrolDetails(
                placeId = placeId.orEmpty(),
                name = null,
                address = null,
                phone = null,
                rating = null,
                ratingCount = null,
                websiteUri = null,
                googleMapsUri = null,
                isOpenNow = null,
                weekdayHours = emptyList(),
                businessStatus = null,
                mapPreviewBitmap = mapPreview
            ).takeIf { mapPreview != null }
        }

        val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.NATIONAL_PHONE_NUMBER,
            Place.Field.INTERNATIONAL_PHONE_NUMBER,
            Place.Field.RATING,
            Place.Field.USER_RATING_COUNT,
            Place.Field.WEBSITE_URI,
            Place.Field.GOOGLE_MAPS_URI,
            Place.Field.CURRENT_OPENING_HOURS,
            Place.Field.OPENING_HOURS,
            Place.Field.UTC_OFFSET,
            Place.Field.BUSINESS_STATUS,
            Place.Field.LOCATION,
            Place.Field.PHOTO_METADATAS
        )
        val request = FetchPlaceRequest.builder(placeId, fields).build()
        runCatching {
            val place = client.fetchPlace(request).awaitTask().place
            val hours = place.currentOpeningHours?.weekdayText
                ?: place.openingHours?.weekdayText
                ?: emptyList()
            val resolvedOpen = place.isOpen
                ?: runCatching {
                    client.isOpen(IsOpenRequest.newInstance(placeId)).awaitTask().isOpen
                }.getOrNull()
                ?: when (GoogleWeekdayHoursParser.statusNow(hours)) {
                    OpeningHoursEvaluator.Status.OPEN -> true
                    OpeningHoursEvaluator.Status.CLOSED -> false
                    OpeningHoursEvaluator.Status.UNKNOWN -> null
                }
            val photo = place.photoMetadatas?.firstOrNull()?.let { meta ->
                runCatching {
                    val photoRequest = FetchPhotoRequest.builder(meta)
                        .setMaxWidth(1200)
                        .setMaxHeight(800)
                        .build()
                    client.fetchPhoto(photoRequest).awaitTask().bitmap
                }.onFailure {
                    AppLogger.w(AppLogger.Category.UI, "Petrol place photo failed for $placeId", it)
                }.getOrNull()
            }
            val lat = place.location?.latitude ?: latitude
            val lng = place.location?.longitude ?: longitude
            GooglePetrolDetails(
                placeId = place.id ?: placeId,
                name = place.displayName,
                address = place.formattedAddress,
                phone = place.nationalPhoneNumber
                    ?: place.internationalPhoneNumber,
                rating = place.rating?.toDouble(),
                ratingCount = place.userRatingCount,
                websiteUri = place.websiteUri?.toString(),
                googleMapsUri = place.googleMapsUri?.toString(),
                isOpenNow = resolvedOpen,
                weekdayHours = hours,
                businessStatus = place.businessStatus?.name,
                photoBitmap = photo,
                mapPreviewBitmap = mapPreview ?: fetchStaticMapPreview(lat, lng)
            )
        }.getOrElse {
            AppLogger.w(AppLogger.Category.UI, "Petrol Place details failed for $placeId", it)
            GooglePetrolDetails(
                placeId = placeId,
                name = null,
                address = null,
                phone = null,
                rating = null,
                ratingCount = null,
                websiteUri = null,
                googleMapsUri = null,
                isOpenNow = null,
                weekdayHours = emptyList(),
                businessStatus = null,
                mapPreviewBitmap = mapPreview
            ).takeIf { mapPreview != null }
        }
    }

    private fun fetchStaticMapPreview(latitude: Double, longitude: Double): Bitmap? {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: return null
        val url =
            "https://maps.googleapis.com/maps/api/staticmap" +
                "?center=$latitude,$longitude" +
                "&zoom=17" +
                "&size=640x360" +
                "&scale=2" +
                "&maptype=roadmap" +
                "&style=feature:poi|visibility:simplified" +
                "&markers=color:0x00E5A0%7C$latitude,$longitude" +
                "&key=$key"
        return runCatching {
            val response = httpClient.newCall(Request.Builder().url(url).get().build()).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    AppLogger.w(
                        AppLogger.Category.UI,
                        "Static map preview failed: HTTP ${resp.code}"
                    )
                    return@runCatching null
                }
                resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
            }
        }.onFailure {
            AppLogger.w(AppLogger.Category.UI, "Static map preview failed", it)
        }.getOrNull()
    }

    fun nearestMatch(
        latitude: Double,
        longitude: Double,
        candidates: List<GooglePetrolPlace>,
        maxMeters: Double = MATCH_RADIUS_METERS
    ): GooglePetrolPlace? {
        return candidates
            .map { it to haversineMeters(latitude, longitude, it.latitude, it.longitude) }
            .filter { it.second <= maxMeters }
            .minByOrNull { it.second }
            ?.first
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result -> cont.resume(result) }
            addOnFailureListener { error -> cont.resumeWith(Result.failure(error)) }
            addOnCanceledListener { cont.cancel() }
        }

    companion object {
        const val MATCH_RADIUS_METERS = 90.0
        private const val MAX_OPEN_LOOKUPS = 12

        private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earth = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            return earth * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
