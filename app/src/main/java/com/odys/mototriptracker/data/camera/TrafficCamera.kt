package com.odys.mototriptracker.data.camera

import com.odys.mototriptracker.data.navigation.NavigationState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class TrafficCameraKind {
    Speed,
    RedLight,
}

data class TrafficCamera(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val kind: TrafficCameraKind,
) {
    val speakText: String
        get() = when (kind) {
            TrafficCameraKind.Speed -> "Speed camera ahead"
            TrafficCameraKind.RedLight -> "Red light camera ahead"
        }

    fun bannerText(distanceMeters: Double): String {
        val distance = NavigationState.formatDistance(distanceMeters)
        return when (kind) {
            TrafficCameraKind.Speed -> "Speed camera · $distance"
            TrafficCameraKind.RedLight -> "Red light camera · $distance"
        }
    }
}

data class TrafficCameraAlert(
    val camera: TrafficCamera,
    val distanceMeters: Double,
) {
    val bannerText: String get() = camera.bannerText(distanceMeters)
}

/** Pure helpers for warn distance, heading filter, and OSM tag mapping. */
object TrafficCameraLogic {
    const val MIN_WARN_METERS = 250.0
    const val MAX_WARN_METERS = 700.0
    const val WARN_LEAD_TIME_SECONDS = 8.0
    const val SLOW_SPEED_MPS = 3.0
    const val AHEAD_HEADING_TOLERANCE_DEGREES = 45.0

    fun warnDistanceMeters(speedMps: Float): Double {
        if (!speedMps.isFinite() || speedMps <= 0f) return MIN_WARN_METERS
        val raw = speedMps * WARN_LEAD_TIME_SECONDS
        return raw.coerceIn(MIN_WARN_METERS, MAX_WARN_METERS)
    }

    /** Absolute smallest angle between two bearings in degrees [0, 180]. */
    fun headingDeltaDegrees(a: Double, b: Double): Double {
        var delta = kotlin.math.abs(a - b) % 360.0
        if (delta > 180.0) delta = 360.0 - delta
        return delta
    }

    fun isAhead(
        riderHeadingDegrees: Float,
        bearingToCameraDegrees: Double,
        speedMps: Float,
    ): Boolean {
        if (!speedMps.isFinite() || speedMps < SLOW_SPEED_MPS) return true
        if (!riderHeadingDegrees.isFinite() || riderHeadingDegrees < 0f) return true
        return headingDeltaDegrees(
            riderHeadingDegrees.toDouble(),
            bearingToCameraDegrees,
        ) <= AHEAD_HEADING_TOLERANCE_DEGREES
    }

    fun bearingDegrees(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Double {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLon = Math.toRadians(toLng - fromLng)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    fun kindFromOsmTags(tags: Map<String, String>): TrafficCameraKind? {
        if (tags["highway"] == "speed_camera") return TrafficCameraKind.Speed
        return when (tags["enforcement"]) {
            "maxspeed" -> TrafficCameraKind.Speed
            "traffic_signals" -> TrafficCameraKind.RedLight
            else -> null
        }
    }
}
