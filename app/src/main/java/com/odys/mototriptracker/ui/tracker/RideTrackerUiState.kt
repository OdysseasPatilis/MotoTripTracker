package com.odys.mototriptracker.ui.tracker

import com.odys.mototriptracker.data.navigation.NavigationState
import com.odys.mototriptracker.data.petrol.PetrolSearchPlan
import com.odys.mototriptracker.data.petrol.RankedPetrolStation
import com.odys.mototriptracker.data.weather.RouteWeatherState
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.domain.TripStats

data class RideTrackerUiState(
    val stats: TripStats = TripStats(),
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val routeCoordinates: List<RouteCoordinate> = emptyList(),
    val navigation: NavigationState = NavigationState(),
    val weather: RouteWeatherState = RouteWeatherState(),
    val discardBanner: String? = null,
    val showDestinationSearch: Boolean = false,
    val showFuelSettings: Boolean = false,
    val showRouteWeather: Boolean = false,
    val showPetrolStations: Boolean = false,
    val petrolStations: List<RankedPetrolStation> = emptyList(),
    val petrolPlan: PetrolSearchPlan? = null,
    val petrolLoading: Boolean = false,
    val petrolDetails: com.odys.mototriptracker.data.petrol.GooglePetrolDetails? = null,
    val petrolDetailsLoading: Boolean = false,
    val petrolMessage: String? = null,
    val tankCapacityLiters: Double = 16.0,
    val fuelRemainingLiters: Double = 16.0,
    val fuelConsumption: Double = 5.5,
    val fuelRangeSummary: String = "",
    val isLowFuel: Boolean = false,
    val preferredBrands: List<String> = listOf("Shell", "BP"),
    val preferredOctanes: Set<Int> = setOf(98, 100),
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastBearing: Float = 0f,
    val lastSpeedMps: Float = 0f
)
