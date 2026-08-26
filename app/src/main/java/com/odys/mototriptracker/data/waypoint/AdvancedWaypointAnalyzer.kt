package com.odys.mototriptracker.data.waypoint

import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.util.AppLogger
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AdvancedWaypointAnalyzer {
    companion object {
        private const val STOP_SPEED_THRESHOLD = 0.5f // m/s

        fun analyzeAndMarkWaypoints(
            context: Context,
            points: List<RoutePointEntity>,
            totalDistanceMeters: Float
        ): List<RoutePointEntity> {

            if (points.isEmpty()) {
                AppLogger.d(AppLogger.Category.WAYPOINT, "No points to analyze")
                return emptyList()
            }

            AppLogger.i(
                AppLogger.Category.WAYPOINT,
                "Analyzing ${points.size} points (dist=${totalDistanceMeters}m)"
            )

            val pointsToUpdate = mutableListOf<RoutePointEntity>()

            // --- HELPER: Reverse Geocoding ---
            fun getStreetName(lat: Double, lng: Double): String {
                val coordinateFallback = String.format(Locale.getDefault(), "%.4f° N, %.4f° E", lat, lng)

                // ==========================================
                // ATTEMPT 1: Google Maps REST API
                // ==========================================
                try {
                    val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")

                    if (!apiKey.isNullOrEmpty()) {
                        val url = URL("https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=$apiKey")

                        // Open connection with strict timeouts so the app doesn't hang on bad networks
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 3000 // Give up after 3 seconds
                        connection.readTimeout = 3000

                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                            val jsonObject = JSONObject(jsonResponse)

                            if (jsonObject.getString("status") == "OK") {
                                val results = jsonObject.getJSONArray("results")
                                if (results.length() > 0) {
                                    val fullAddress = results.getJSONObject(0).getString("formatted_address")
                                    return fullAddress.split(",").first().trim() // e.g., "Agiou Dimitriou 15"
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(
                        AppLogger.Category.WAYPOINT,
                        "Maps geocode failed (${e.message}) — falling back to native Geocoder"
                    )
                }

                // ==========================================
                // ATTEMPT 2: Android OS Native Geocoder
                // ==========================================
                try {
                    if (Geocoder.isPresent()) {
                        val geocoder = Geocoder(context, Locale.getDefault())

                        // We use the synchronous call here. It is safe because TripRepository
                        // runs this entire analyzer on a Dispatchers.IO background thread!
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lng, 1)

                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val nativeAddress = address.thoroughfare ?: address.subLocality ?: address.locality

                            if (nativeAddress != null) {
                                AppLogger.d(AppLogger.Category.WAYPOINT, "Native geocode ok: $nativeAddress")
                                return nativeAddress
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(
                        AppLogger.Category.WAYPOINT,
                        "Native geocode failed (${e.message}) — using coordinates"
                    )
                }

                // ==========================================
                // ATTEMPT 3: GPS Coordinates (Safe Fallback)
                // ==========================================
                return coordinateFallback
            }

            // --- 1. DEPARTURE ---
            val startPoint = points.first()
            startPoint.isWaypoint = true
            startPoint.waypointType = "START"
            startPoint.waypointTitle = "Departure"
            startPoint.waypointSubtitle = getStreetName(startPoint.latitude, startPoint.longitude)
            pointsToUpdate.add(startPoint)

            // --- 2. THE HIGHLIGHTS (Top Speed & Summit) ---

            // Top Speed (Only mark if they went faster than 60 km/h)
            val topSpeedPoint = points.maxByOrNull { it.speedMps }
            if (topSpeedPoint != null && (topSpeedPoint.speedMps * 3.6f) > 100f) {
                topSpeedPoint.isWaypoint = true
                topSpeedPoint.waypointType = "TOP_SPEED"
                topSpeedPoint.waypointTitle = "Top Speed Hit"
                topSpeedPoint.waypointSubtitle = String.format(Locale.getDefault(), "%.1f km/h", topSpeedPoint.speedMps * 3.6f)
                pointsToUpdate.add(topSpeedPoint)
            }

            // Summit (Only mark if the highest point is at least 100m higher than where they started)
            val summitPoint = points.maxByOrNull { it.altitude }
            val startAltitude = startPoint.altitude
            if (summitPoint != null && summitPoint.altitude > (startAltitude + 100.0)) {
                summitPoint.isWaypoint = true
                summitPoint.waypointType = "SUMMIT"
                summitPoint.waypointTitle = "Highest Elevation"
                summitPoint.waypointSubtitle = "${summitPoint.altitude.toInt()}m above sea level"
                pointsToUpdate.add(summitPoint)
            }

            // --- 3. SMART STOPS (The State Machine) ---
            var stopStartPoint: RoutePointEntity? = null
            var distanceAtStopStart = 0f

            for (i in 1 until points.size - 1) {
                val point = points[i]

                if (point.speedMps < STOP_SPEED_THRESHOLD) {
                    if (stopStartPoint == null) {
                        stopStartPoint = point
                        distanceAtStopStart = totalDistanceMeters * (i.toFloat() / points.size)
                    }
                } else {
                    if (stopStartPoint != null) {
                        val stopDurationMs = point.timestamp - stopStartPoint.timestamp
                        val kmString = String.format(Locale.getDefault(), "%.1f", distanceAtStopStart / 1000f)

                        // We only care about stops longer than 2 seconds (filters out GPS jitter)
                        if (stopDurationMs > 2000L) {
                            stopStartPoint.isWaypoint = true

                            val minutes = (stopDurationMs / 1000) / 60
                            val seconds = (stopDurationMs / 1000) % 60
                            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

                            // SMART CLASSIFICATION LOGIC
                            when {
                                stopDurationMs < 10_000L -> {
                                    // 2 to 10 seconds
                                    stopStartPoint.waypointType = "STOP_SIGN"
                                    stopStartPoint.waypointTitle = "Stop Sign / Yield"
                                    stopStartPoint.waypointSubtitle = "${kmString}km - $timeStr pause"
                                }
                                stopDurationMs < 60_000L -> {
                                    // 10 to 60 seconds
                                    stopStartPoint.waypointType = "TRAFFIC_LIGHT"
                                    stopStartPoint.waypointTitle = "Traffic Light"
                                    stopStartPoint.waypointSubtitle = "${kmString}km - $timeStr pause"
                                }
                                stopDurationMs < 300_000L -> {
                                    // 1 to 5 minutes
                                    stopStartPoint.waypointType = "BRIEF_STOP"
                                    stopStartPoint.waypointTitle = "Brief Stop"
                                    stopStartPoint.waypointSubtitle = "${kmString}km - $timeStr pause"
                                }
                                else -> {
                                    // Over 5 minutes! Geocode this location because it's a major stop.
                                    stopStartPoint.waypointType = "REST_STOP"
                                    stopStartPoint.waypointTitle = "Rest Stop"
                                    val address = getStreetName(stopStartPoint.latitude, stopStartPoint.longitude)
                                    stopStartPoint.waypointSubtitle = "$address - $timeStr pause"
                                }
                            }
                            pointsToUpdate.add(stopStartPoint)
                        }
                        stopStartPoint = null
                    }
                }
            }

            // --- 4. ARRIVAL ---
            val endPoint = points.last()
            endPoint.isWaypoint = true
            endPoint.waypointType = "END"
            endPoint.waypointTitle = "Arrival"
            endPoint.waypointSubtitle = getStreetName(endPoint.latitude, endPoint.longitude)
            pointsToUpdate.add(endPoint)

            // Remove duplicates just in case the Top Speed or Summit happened EXACTLY at the Arrival/Departure
            return pointsToUpdate.distinctBy { it.id }
        }
    }
}