package com.odys.mototriptracker.data.navigation

import com.odys.mototriptracker.domain.RouteCoordinate
import java.util.Locale
import java.util.UUID

data class NavigationSearchResult(
    val placeId: String,
    val title: String,
    val subtitle: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class NavStep(
    val id: String = UUID.randomUUID().toString(),
    val instruction: String,
    val distanceMeters: Double,
    val endLatitude: Double,
    val endLongitude: Double
)

enum class NavigationPhase {
    Idle,
    Previewing,
    Navigating,
}

data class NavRouteOption(
    val id: String = UUID.randomUUID().toString(),
    val coordinates: List<RouteCoordinate>,
    val distanceMeters: Double,
    val expectedTravelTimeSeconds: Double,
    val steps: List<NavStep>,
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
    val distanceToNextManeuverMeters: Double = 0.0,
    val phase: NavigationPhase = NavigationPhase.Idle,
    val previewRoutes: List<NavRouteOption> = emptyList(),
    val selectedRouteId: String? = null,
    val previewErrorMessage: String? = null,
) {
    val hasDestination: Boolean = destinationLatitude != null && destinationLongitude != null
    val hasRoute: Boolean = routeCoordinates.size > 1
    val currentStep: NavStep? = steps.getOrNull(currentStepIndex)
    val isPreviewing: Boolean = phase == NavigationPhase.Previewing
    val isNavigating: Boolean = phase == NavigationPhase.Navigating
    val selectedPreviewRoute: NavRouteOption?
        get() = previewRoutes.firstOrNull { it.id == selectedRouteId } ?: previewRoutes.firstOrNull()

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

        fun formatDuration(seconds: Double): String {
            val minutes = maxOf(1, (seconds / 60.0).toInt())
            return "$minutes min"
        }
    }
}

enum class PetrolSearchOutcome {
    FOUND,
    NONE_NEARBY,
    ALL_CLOSED
}
