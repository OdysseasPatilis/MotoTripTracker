package com.odys.mototriptracker.domain

/**
 * Pure camera framing for ride-follow: look-ahead center + cruise / turn-approach distance.
 * Mirrors iOS `RideFollowCameraPolicy`.
 */
object RideFollowCameraPolicy {
    // Cruise distance
    private const val CRUISE_BASE_METERS = 350.0
    private const val CRUISE_SPEED_FACTOR = 7.0
    private const val CRUISE_SPEED_CAP_KMH = 180.0

    // Look-ahead
    private const val LOOK_AHEAD_BASE_METERS = 10.0
    private const val LOOK_AHEAD_SPEED_SLOPE = 1.7
    private const val LOOK_AHEAD_SPEED_CAP_KMH = 160.0
    private const val LOOK_AHEAD_NAV_MULTIPLIER = 1.2

    // Approach window
    private const val APPROACH_WINDOW_BASE_METERS = 20.0
    private const val APPROACH_WINDOW_SPEED_FACTOR = 2.95
    private const val APPROACH_WINDOW_SPEED_CAP_KMH = 160.0
    private const val APPROACH_WINDOW_MIN_METERS = 120.0
    private const val APPROACH_WINDOW_MAX_METERS = 400.0

    // Turn zoom
    private const val TURN_ZOOM_CLOSEST_RATIO = 0.55
    private const val TURN_ZOOM_CLOSEST_FLOOR_METERS = 220.0

    // Map camera framing (Android Maps zoom/tilt)
    private const val RIDING_TILT_DEGREES = 55f
    private const val IDLE_ZOOM = 14.5f
    private const val IDLE_TILT_DEGREES = 0f

    data class LatLngDegrees(val latitude: Double, val longitude: Double)

    /** Platform-agnostic follow / idle camera framing for the live map. */
    data class CameraFraming(
        val targetLatitude: Double,
        val targetLongitude: Double,
        val zoom: Float,
        val bearingDegrees: Float,
        val tiltDegrees: Float,
    )

    /** Preserve today's riding pull-back curve. */
    fun cruiseDistanceMeters(speedKmh: Double): Double =
        CRUISE_BASE_METERS + minOf(maxOf(speedKmh, 0.0), CRUISE_SPEED_CAP_KMH) * CRUISE_SPEED_FACTOR

    /** Meters ahead of the rider for map center (speed-scaled; +20% when navigating). */
    fun lookAheadMeters(speedKmh: Double, isNavigating: Boolean): Double {
        val speed = maxOf(speedKmh, 0.0)
        // ~40 m @ 20 km/h → ~180 m @ 100 km/h
        val base = LOOK_AHEAD_BASE_METERS + minOf(speed, LOOK_AHEAD_SPEED_CAP_KMH) * LOOK_AHEAD_SPEED_SLOPE
        return if (isNavigating) base * LOOK_AHEAD_NAV_MULTIPLIER else base
    }

    /** Distance-to-maneuver at which turn zoom begins. */
    fun approachWindowMeters(speedKmh: Double): Double {
        val speed = minOf(maxOf(speedKmh, 0.0), APPROACH_WINDOW_SPEED_CAP_KMH)
        // 40→135, 80→250, 120→375
        val window = APPROACH_WINDOW_BASE_METERS + speed * APPROACH_WINDOW_SPEED_FACTOR
        return window.coerceIn(APPROACH_WINDOW_MIN_METERS, APPROACH_WINDOW_MAX_METERS)
    }

    fun cameraDistanceMeters(
        speedKmh: Double,
        distanceToNextManeuver: Double?,
        isNavigating: Boolean,
        isRecalculating: Boolean,
    ): Double {
        val cruise = cruiseDistanceMeters(speedKmh)
        if (!isNavigating || isRecalculating) return cruise
        val toTurn = distanceToNextManeuver ?: return cruise
        if (toTurn < 0) return cruise
        val window = approachWindowMeters(speedKmh)
        if (toTurn > window) return cruise

        val closest = maxOf(cruise * TURN_ZOOM_CLOSEST_RATIO, TURN_ZOOM_CLOSEST_FLOOR_METERS)
        val progress = (1.0 - (toTurn / window)).coerceIn(0.0, 1.0)
        return cruise + (closest - cruise) * progress
    }

    fun centerCoordinate(
        riderLat: Double,
        riderLng: Double,
        courseDegrees: Float,
        speedKmh: Double,
        isNavigating: Boolean,
    ): LatLngDegrees {
        if (!courseDegrees.isFinite() || courseDegrees < 0f) {
            return LatLngDegrees(riderLat, riderLng)
        }
        val meters = lookAheadMeters(speedKmh, isNavigating)
        return coordinateAhead(riderLat, riderLng, courseDegrees.toDouble(), meters)
    }

    /** Maps camera distance (meters) to Google Maps zoom level. */
    fun zoomFromDistanceMeters(distanceMeters: Double): Float = when {
        distanceMeters <= 400 -> 17.5f
        distanceMeters <= 700 -> 16.8f
        distanceMeters <= 1000 -> 16.2f
        distanceMeters <= 1400 -> 15.6f
        else -> 15f
    }

    /**
     * Single entry for live-map follow / recenter: riding uses look-ahead + turn zoom;
     * idle centers on the rider with a flat overview.
     */
    fun followCameraFraming(
        riderLat: Double,
        riderLng: Double,
        courseDegrees: Float,
        speedKmh: Double,
        isRiding: Boolean,
        isNavigating: Boolean,
        isRecalculating: Boolean,
        distanceToNextManeuverMeters: Double?,
    ): CameraFraming {
        if (!isRiding) {
            return CameraFraming(
                targetLatitude = riderLat,
                targetLongitude = riderLng,
                zoom = IDLE_ZOOM,
                bearingDegrees = 0f,
                tiltDegrees = IDLE_TILT_DEGREES,
            )
        }
        val center = centerCoordinate(
            riderLat = riderLat,
            riderLng = riderLng,
            courseDegrees = courseDegrees,
            speedKmh = speedKmh,
            isNavigating = isNavigating,
        )
        val distance = cameraDistanceMeters(
            speedKmh = speedKmh,
            distanceToNextManeuver = if (isNavigating) distanceToNextManeuverMeters else null,
            isNavigating = isNavigating,
            isRecalculating = isRecalculating,
        )
        return CameraFraming(
            targetLatitude = center.latitude,
            targetLongitude = center.longitude,
            zoom = zoomFromDistanceMeters(distance),
            bearingDegrees = if (courseDegrees >= 0f) courseDegrees else 0f,
            tiltDegrees = RIDING_TILT_DEGREES,
        )
    }

    fun coordinateAhead(
        latitude: Double,
        longitude: Double,
        courseDegrees: Double,
        meters: Double,
    ): LatLngDegrees {
        val (lat, lng) = Geo.destination(latitude, longitude, courseDegrees, meters)
        return LatLngDegrees(lat, lng)
    }
}
