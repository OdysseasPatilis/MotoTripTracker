package com.odys.mototriptracker.data.navigation

import com.odys.mototriptracker.domain.RouteCoordinate

data class NavigationSearchResult(
    val placeId: String,
    val title: String,
    val subtitle: String
)

data class NavigationState(
    val searchQuery: String = "",
    val searchResults: List<NavigationSearchResult> = emptyList(),
    val destinationName: String? = null,
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val routeCoordinates: List<RouteCoordinate> = emptyList(),
    val distanceRemainingMeters: Double = 0.0,
    val etaEpochMs: Long? = null,
    val isRouting: Boolean = false
) {
    val hasDestination: Boolean = destinationLatitude != null && destinationLongitude != null
    val hasRoute: Boolean = routeCoordinates.size > 1

    val summaryText: String
        get() {
            val distanceString = if (distanceRemainingMeters >= 1000) {
                String.format("%.1f km", distanceRemainingMeters / 1000.0)
            } else {
                "${distanceRemainingMeters.toInt()} m"
            }
            val eta = etaEpochMs ?: return distanceString
            val time = java.text.SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                .format(java.util.Date(eta))
            return "$distanceString · ETA $time"
        }
}
