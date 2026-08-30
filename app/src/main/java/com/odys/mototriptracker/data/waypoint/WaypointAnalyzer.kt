package com.odys.mototriptracker.data.waypoint

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import java.util.Locale

class WaypointAnalyzer {
    companion object {
        // Anything under 0.5 m/s (1.8 km/h) is considered "Stopped" (accounts for GPS drift)
        private const val STOP_SPEED_THRESHOLD = 0.5f

        // You have to be stopped for at least 30 seconds for it to count as a waypoint
        private const val MIN_STOP_DURATION_MS = 30_000L

        fun analyzeAndMarkWaypoints(
            points: List<RoutePointEntity>,
            totalDistanceMeters: Float,
            totalElevationGain: Float
        ): List<RoutePointEntity> {

            if (points.isEmpty()) return emptyList()

            val pointsToUpdate = mutableListOf<RoutePointEntity>()

            // --- 1. DEPARTURE WAYPOINT ---
            val startPoint = points.first()
            startPoint.isWaypoint = true
            startPoint.waypointTitle = "Departure"
            startPoint.waypointSubtitle = "0.0 km"
            pointsToUpdate.add(startPoint)

            // --- 2. BRIEF STOPS (The State Machine) ---
            var stopStartPoint: RoutePointEntity? = null
            var distanceAtStopStart = 0f

            for (i in 1 until points.size - 1) {
                val point = points[i]

                if (point.speedMps < STOP_SPEED_THRESHOLD) {
                    // STATE: We are stopped.
                    if (stopStartPoint == null) {
                        stopStartPoint = point // Mark the exact moment we stopped

                        // Rough estimate of distance at this point (assuming steady pace,
                        // though you could calculate exact distance if you prefer)
                        distanceAtStopStart = totalDistanceMeters * (i.toFloat() / points.size)
                    }
                } else {
                    // STATE: We are moving.
                    if (stopStartPoint != null) {
                        // We JUST started moving again. How long were we stopped for?
                        val stopDurationMs = point.timestamp - stopStartPoint.timestamp

                        if (stopDurationMs >= MIN_STOP_DURATION_MS) {
                            // It was a valid stop! Mark the starting point as a Waypoint.
                            stopStartPoint.isWaypoint = true

                            val minutes = (stopDurationMs / 1000) / 60
                            val seconds = (stopDurationMs / 1000) % 60
                            val timeString = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                            val kmString = String.format(Locale.US, "%.1f", distanceAtStopStart / 1000f)

                            // Title logic based on duration
                            if (minutes >= 5) {
                                stopStartPoint.waypointTitle = "Rest stop"
                                stopStartPoint.waypointSubtitle = "${kmString}km - $timeString pause"
                            } else {
                                stopStartPoint.waypointTitle = "Brief stop"
                                stopStartPoint.waypointSubtitle = "Traffic light - ${kmString}km - $timeString pause"
                            }

                            pointsToUpdate.add(stopStartPoint)
                        }

                        // Reset the state machine for the next stop
                        stopStartPoint = null
                    }
                }
            }

            // --- 3. ARRIVAL WAYPOINT ---
            if (points.size > 1) {
                val endPoint = points.last()
                val totalKmString = String.format(Locale.US, "%.1f", totalDistanceMeters / 1000f)

                endPoint.isWaypoint = true
                endPoint.waypointTitle = "Arrival"
                endPoint.waypointSubtitle = "Destination - ${totalKmString}km - +${totalElevationGain.toInt()}m elev."
                pointsToUpdate.add(endPoint)
            }

            return pointsToUpdate
        }
    }
}