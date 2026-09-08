package com.odys.mototriptracker.data.waypoint

import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import com.odys.mototriptracker.util.AppLogger
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Resolves a short road / place label for waypoint subtitles.
 * Prefers street/route names over raw coordinates.
 */
object WaypointReverseGeocoder {

    private val COORDINATE_LABEL =
        Regex("""\d+[.,]\d+\s*°\s*[NSns].*\d+[.,]\d+\s*°\s*[EWew]""")

    fun looksLikeCoordinates(label: String): Boolean =
        COORDINATE_LABEL.containsMatchIn(label.trim())

    fun coordinateFallback(lat: Double, lng: Double): String =
        String.format(Locale.getDefault(), "%.4f° N, %.4f° E", lat, lng)

    fun resolveRoadName(context: Context, lat: Double, lng: Double): String {
        val fallback = coordinateFallback(lat, lng)

        resolveViaGoogle(context, lat, lng)?.let { return it }
        resolveViaAndroidGeocoder(context, lat, lng)?.let { return it }
        resolveViaNominatim(lat, lng)?.let { return it }

        AppLogger.w(
            AppLogger.Category.WAYPOINT,
            "Reverse geocode exhausted @ ${AppLogger.coordinate(lat, lng)} — using coordinates"
        )
        return fallback
    }

    private fun resolveViaGoogle(context: Context, lat: Double, lng: Double): String? {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
                ?.takeIf { it.isNotBlank() }
                ?: return null

            val url = URL(
                "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=$apiKey"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseGoogleGeocodeRoadName(body)
            } finally {
                connection.disconnect()
            }
        }.onFailure {
            AppLogger.w(AppLogger.Category.WAYPOINT, "Maps geocode failed (${it.message})")
        }.getOrNull()
    }

    /** Visible for tests — picks route / street from Geocoding JSON. */
    fun parseGoogleGeocodeRoadName(jsonBody: String): String? {
        val root = JSONObject(jsonBody)
        if (root.optString("status") != "OK") return null
        val results = root.optJSONArray("results") ?: return null
        if (results.length() == 0) return null

        for (ri in 0 until results.length()) {
            val result = results.getJSONObject(ri)
            val components = result.optJSONArray("address_components") ?: continue
            var route: String? = null
            var streetNumber: String? = null
            var neighborhood: String? = null
            var locality: String? = null
            for (ci in 0 until components.length()) {
                val component = components.getJSONObject(ci)
                val types = component.optJSONArray("types") ?: continue
                val typeSet = buildSet {
                    for (ti in 0 until types.length()) add(types.getString(ti))
                }
                val name = component.optString("long_name").takeIf { it.isNotBlank() } ?: continue
                when {
                    "route" in typeSet -> route = name
                    "street_number" in typeSet -> streetNumber = name
                    "neighborhood" in typeSet || "sublocality" in typeSet ||
                        "sublocality_level_1" in typeSet -> neighborhood = neighborhood ?: name
                    "locality" in typeSet -> locality = name
                }
            }
            val road = when {
                route != null && streetNumber != null -> "$route $streetNumber"
                route != null -> route
                neighborhood != null -> neighborhood
                locality != null -> locality
                else -> null
            }
            if (!road.isNullOrBlank()) return road.trim()

            val formatted = result.optString("formatted_address")
                .split(",")
                .firstOrNull()
                ?.trim()
                .orEmpty()
            if (formatted.isNotBlank() && !looksLikeCoordinates(formatted)) return formatted
        }
        return null
    }

    private fun resolveViaAndroidGeocoder(context: Context, lat: Double, lng: Double): String? {
        return runCatching {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1) ?: return null
            val address = addresses.firstOrNull() ?: return null
            val road = listOfNotNull(
                address.thoroughfare?.let { street ->
                    address.subThoroughfare?.let { "$street $it" } ?: street
                },
                address.featureName?.takeIf {
                    it.isNotBlank() &&
                        !it.equals(address.thoroughfare, ignoreCase = true) &&
                        !looksLikeCoordinates(it) &&
                        it.any { ch -> ch.isLetter() }
                },
                address.subLocality,
                address.locality,
            ).firstOrNull { !it.isNullOrBlank() }
            road?.trim()?.takeIf { it.isNotBlank() && !looksLikeCoordinates(it) }
        }.onFailure {
            AppLogger.w(AppLogger.Category.WAYPOINT, "Native geocode failed (${it.message})")
        }.getOrNull()
    }

    private fun resolveViaNominatim(lat: Double, lng: Double): String? {
        return runCatching {
            val url = URL(
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
                    "&lat=$lat&lon=$lng&zoom=18&addressdetails=1"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MotoTripTracker/1.0 (Android; ride waypoints)")
                setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseNominatimRoadName(body)
            } finally {
                connection.disconnect()
            }
        }.onFailure {
            AppLogger.w(AppLogger.Category.WAYPOINT, "Nominatim reverse failed (${it.message})")
        }.getOrNull()
    }

    fun parseNominatimRoadName(jsonBody: String): String? {
        val root = JSONObject(jsonBody)
        val address = root.optJSONObject("address") ?: return null
        val road = sequenceOf(
            address.optString("road"),
            address.optString("pedestrian"),
            address.optString("path"),
            address.optString("neighbourhood"),
            address.optString("suburb"),
            address.optString("village"),
            address.optString("town"),
            address.optString("city"),
        ).map { it.trim() }.firstOrNull { it.isNotBlank() }
        return road?.takeIf { !looksLikeCoordinates(it) }
    }
}
