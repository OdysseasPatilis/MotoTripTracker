package com.odys.mototriptracker.domain.model

/**
 * Persistence-agnostic trip summary. UI and domain use this; ObjectBox entities
 * stay inside the data layer.
 */
data class Trip(
    val id: Long = 0,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val distanceMeters: Float = 0f,
    val movingTime: Long = 0,
    val stoppedTime: Long = 0,
    val maxSpeed: Float = 0f,
    val maxGForce: Float = 0f,
    val elevationGain: Float = 0f,
    val avgSpeed: Float = 0f,
    val encodedRoutePolyline: String? = "",
    val title: String? = null,
    val isFavorite: Boolean = false,
    val maxLateralGForce: Float = 0f,
    val cornerCount: Int = 0,
    val twistinessScore: Float = 0f,
)

/** Single GPS sample along a recorded ride (and optional waypoint metadata). */
data class RoutePoint(
    val id: Long = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speedMps: Float = 0f,
    val timestamp: Long = 0,
    val waypointType: String? = null,
    val isWaypoint: Boolean = false,
    val waypointTitle: String = "",
    val waypointSubtitle: String = "",
)

/** Display title for list/summary/share — prefers custom name, else dated default. */
fun Trip.displayTitle(): String =
    title?.takeIf { it.isNotBlank() }
        ?: run {
            val formatter = java.text.SimpleDateFormat("MMM dd, yyyy - HH:mm", java.util.Locale.getDefault())
            "Ride ${formatter.format(startTime)}"
        }
