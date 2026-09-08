package com.odys.mototriptracker.data.waypoint

import android.content.Context
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.util.AppLogger
import java.util.Locale

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

            // --- 1. DEPARTURE ---
            val startPoint = points.first()
            startPoint.isWaypoint = true
            startPoint.waypointType = "START"
            startPoint.waypointTitle = "Departure"
            startPoint.waypointSubtitle = WaypointReverseGeocoder.resolveRoadName(
                context,
                startPoint.latitude,
                startPoint.longitude
            )
            pointsToUpdate.add(startPoint)

            // --- 2. THE HIGHLIGHTS (Top Speed & Summit) ---

            val topSpeedPoint = points.maxByOrNull { it.speedMps }
            if (topSpeedPoint != null && (topSpeedPoint.speedMps * 3.6f) > 100f) {
                topSpeedPoint.isWaypoint = true
                topSpeedPoint.waypointType = "TOP_SPEED"
                topSpeedPoint.waypointTitle = "Top Speed Hit"
                topSpeedPoint.waypointSubtitle =
                    String.format(Locale.getDefault(), "%.1f km/h", topSpeedPoint.speedMps * 3.6f)
                pointsToUpdate.add(topSpeedPoint)
            }

            val summitPoint = points.maxByOrNull { it.altitude }
            val startAltitude = startPoint.altitude
            if (summitPoint != null && summitPoint.altitude > (startAltitude + 100.0)) {
                summitPoint.isWaypoint = true
                summitPoint.waypointType = "SUMMIT"
                summitPoint.waypointTitle = "Highest Elevation"
                summitPoint.waypointSubtitle = "${summitPoint.altitude.toInt()}m above sea level"
                pointsToUpdate.add(summitPoint)
            }

            // --- 3. SMART STOPS ---
            var stopStartPoint: RoutePointEntity? = null
            var distanceAtStopStart = 0f
            val restStops = mutableListOf<RoutePointEntity>()

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

                        if (stopDurationMs > 2000L) {
                            stopStartPoint.isWaypoint = true

                            val minutes = (stopDurationMs / 1000) / 60
                            val seconds = (stopDurationMs / 1000) % 60
                            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

                            when {
                                stopDurationMs < 10_000L -> {
                                    stopStartPoint.waypointType = "STOP_SIGN"
                                    stopStartPoint.waypointTitle = "Stop Sign / Yield"
                                    stopStartPoint.waypointSubtitle = "${kmString}km - $timeStr pause"
                                }
                                stopDurationMs < 60_000L -> {
                                    stopStartPoint.waypointType = "TRAFFIC_LIGHT"
                                    stopStartPoint.waypointTitle = "Traffic Light"
                                    stopStartPoint.waypointSubtitle = "${kmString}km - $timeStr pause"
                                }
                                stopDurationMs < 300_000L -> {
                                    stopStartPoint.waypointType = "BRIEF_STOP"
                                    stopStartPoint.waypointTitle = "Brief Stop"
                                    stopStartPoint.waypointSubtitle = "${kmString}km - $timeStr pause"
                                }
                                else -> {
                                    stopStartPoint.waypointType = "REST_STOP"
                                    stopStartPoint.waypointTitle = "Rest Stop"
                                    stopStartPoint.waypointSubtitle = "${kmString}km - $timeStr pause"
                                    restStops.add(stopStartPoint)
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
            endPoint.waypointSubtitle = WaypointReverseGeocoder.resolveRoadName(
                context,
                endPoint.latitude,
                endPoint.longitude
            )
            pointsToUpdate.add(endPoint)

            // Geocode a few rest stops only (matches iOS — avoid long finalize stalls).
            for (stop in restStops.take(3)) {
                val address = WaypointReverseGeocoder.resolveRoadName(
                    context,
                    stop.latitude,
                    stop.longitude
                )
                val existing = stop.waypointSubtitle
                stop.waypointSubtitle = if (existing.isBlank()) address else "$address · $existing"
            }

            return pointsToUpdate.distinctBy { it.id }
        }
    }
}
