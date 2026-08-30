package com.odys.mototriptracker.data.navigation

import com.odys.mototriptracker.domain.RouteCoordinate
import java.util.Locale

data class NavigationSearchResult(
    val placeId: String,
    val title: String,
    val subtitle: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class NavStep(
    val id: String = java.util.UUID.randomUUID().toString(),
    val instruction: String,
    val distanceMeters: Double,
    val endLatitude: Double,
    val endLongitude: Double
)

data class NavigationState(
    val searchQuery: String = "",
    val searchResults: List<NavigationSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val destinationName: String? = null,
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val routeCoordinates: List<RouteCoordinate> = emptyList(),
    val distanceRemainingMeters: Double = 0.0,
    val etaEpochMs: Long? = null,
    val isRouting: Boolean = false,
    val isRecalculating: Boolean = false,
    val isOffRoute: Boolean = false,
    val isVoiceEnabled: Boolean = true,
    val steps: List<NavStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val distanceToNextManeuverMeters: Double = 0.0
) {
    val hasDestination: Boolean = destinationLatitude != null && destinationLongitude != null
    val hasRoute: Boolean = routeCoordinates.size > 1
    val currentStep: NavStep? = steps.getOrNull(currentStepIndex)

    val summaryText: String
        get() {
            val distanceString = formatDistance(distanceRemainingMeters)
            val eta = etaEpochMs ?: return distanceString
            val time = java.text.SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                .format(java.util.Date(eta))
            return "$distanceString · ETA $time"
        }

    val guidanceSummary: String
        get() = when {
            isRecalculating -> "Recalculating…"
            isOffRoute -> "Off route — recalculating"
            currentStep != null -> "${formatDistance(distanceToNextManeuverMeters)} · ${currentStep.instruction}"
            else -> summaryText
        }

    companion object {
        fun formatDistance(meters: Double): String = if (meters >= 1000) {
            String.format(Locale.US, "%.1f km", meters / 1000.0)
        } else {
            "${maxOf(0, meters.toInt())} m"
        }
    }
}

enum class PetrolSearchOutcome {
    FOUND,
    NONE_NEARBY,
    ALL_CLOSED
}
