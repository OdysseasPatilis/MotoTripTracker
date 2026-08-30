package com.odys.mototriptracker.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.data.fuel.FuelService
import com.odys.mototriptracker.data.location.LocationRepository
import com.odys.mototriptracker.data.navigation.NavigationService
import com.odys.mototriptracker.data.navigation.NavigationSearchResult
import com.odys.mototriptracker.data.petrol.GooglePetrolDetails
import com.odys.mototriptracker.data.petrol.PetrolPreferences
import com.odys.mototriptracker.data.petrol.PetrolSearchPlan
import com.odys.mototriptracker.data.petrol.PetrolStationFinder
import com.odys.mototriptracker.data.petrol.PetrolStationRecommendation
import com.odys.mototriptracker.data.petrol.RankedPetrolStation
import com.odys.mototriptracker.data.weather.RouteWeatherService
import com.odys.mototriptracker.domain.GpsQuality
import com.odys.mototriptracker.domain.RideSessionState
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.domain.TripManager
import com.odys.mototriptracker.domain.usecase.ObserveRideSessionUseCase
import com.odys.mototriptracker.domain.usecase.PauseRideUseCase
import com.odys.mototriptracker.domain.usecase.ResumeRideUseCase
import com.odys.mototriptracker.domain.usecase.StartRideUseCase
import com.odys.mototriptracker.domain.usecase.StopRideUseCase
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RideTrackerViewModel @Inject constructor(
    observeRideSessionUseCase: ObserveRideSessionUseCase,
    private val tripManager: TripManager,
    private val navigationService: NavigationService,
    private val routeWeatherService: RouteWeatherService,
    private val fuelService: FuelService,
    private val petrolPreferences: PetrolPreferences,
    private val petrolStationFinder: PetrolStationFinder,
    private val locationRepository: LocationRepository,
    private val startRideUseCase: StartRideUseCase,
    private val stopRideUseCase: StopRideUseCase,
    private val pauseRideUseCase: PauseRideUseCase,
    private val resumeRideUseCase: ResumeRideUseCase
) : ViewModel() {
    private val observeRideSession = observeRideSessionUseCase

    private val dashboardGpsAccuracy = MutableStateFlow<Float?>(null)
    private val showDestinationSearch = MutableStateFlow(false)
    private val showFuelSettings = MutableStateFlow(false)
    private val showRouteWeather = MutableStateFlow(false)
    private val showPetrolStations = MutableStateFlow(false)
    private val petrolStations = MutableStateFlow<List<RankedPetrolStation>>(emptyList())
    private val petrolPlan = MutableStateFlow<PetrolSearchPlan?>(null)
    private val petrolLoading = MutableStateFlow(false)
    private val petrolDetails = MutableStateFlow<GooglePetrolDetails?>(null)
    private val petrolDetailsLoading = MutableStateFlow(false)
    private val discardBanner = MutableStateFlow<String?>(null)
    private val petrolMessage = MutableStateFlow<String?>(null)
    private var dashboardLocationJob: Job? = null
    private var petrolSearchJob: Job? = null

    private val rideInputs = combine(
        observeRideSession(),
        tripManager.routeCoordinates,
        navigationService.state,
        routeWeatherService.state,
        locationRepository.lastLocation
    ) { session, routeCoordinates, navigation, weather, lastLocation ->
        RideInputs(session, routeCoordinates, navigation, weather, lastLocation)
    }

    private val fuelInputs = combine(
        fuelService.tankCapacityLiters,
        fuelService.fuelRemainingLiters,
        fuelService.consumptionLPer100Km,
        petrolPreferences.preferredBrands,
        petrolPreferences.preferredOctanes
    ) { tank, remaining, consumption, brands, octanes ->
        FuelPrefs(tank, remaining, consumption, brands, octanes)
    }

    private val coreInputs = combine(rideInputs, fuelInputs, dashboardGpsAccuracy) { ride, fuel, dashAccuracy ->
        CoreInputs(ride, fuel, dashAccuracy)
    }

    private val sheetFlags = combine(
        showDestinationSearch,
        showFuelSettings,
        showRouteWeather,
        showPetrolStations
    ) { search, fuel, weather, petrol ->
        SheetFlags(search, fuel, weather, petrol)
    }

    private val petrolUiCore = combine(
        petrolStations,
        petrolPlan,
        petrolLoading
    ) { stations, plan, loading ->
        Triple(stations, plan, loading)
    }

    private val petrolUiExtras = combine(
        petrolDetails,
        petrolDetailsLoading,
        discardBanner,
        petrolMessage
    ) { details, detailsLoading, banner, message ->
        PetrolExtras(details, detailsLoading, banner, message)
    }

    private val petrolUi = combine(petrolUiCore, petrolUiExtras) { core, extras ->
        PetrolUi(
            stations = core.first,
            plan = core.second,
            loading = core.third,
            details = extras.details,
            detailsLoading = extras.detailsLoading,
            banner = extras.banner,
            message = extras.message
        )
    }

    private val overlayInputs = combine(sheetFlags, petrolUi) { sheets, petrol ->
        OverlayInputs(sheets, petrol)
    }

    val uiState: StateFlow<RideTrackerUiState> = combine(
        coreInputs,
        overlayInputs
    ) { core, overlay ->
        val liveAccuracy = core.ride.lastLocation?.takeIf { it.hasAccuracy() && it.accuracy >= 0f }?.accuracy
            ?: core.dashAccuracy
            ?: core.ride.session.stats.gpsAccuracyMeters

        RideTrackerUiState(
            stats = core.ride.session.stats.copy(
                gpsAccuracyMeters = liveAccuracy,
                gpsQuality = GpsQuality.fromAccuracyMeters(liveAccuracy)
            ),
            isTracking = core.ride.session.isActive,
            isPaused = core.ride.session.isPaused,
            routeCoordinates = core.ride.routeCoordinates,
            navigation = core.ride.navigation,
            weather = core.ride.weather,
            discardBanner = overlay.petrol.banner,
            showDestinationSearch = overlay.sheets.showSearch,
            showFuelSettings = overlay.sheets.showFuel,
            showRouteWeather = overlay.sheets.showWeather,
            showPetrolStations = overlay.sheets.showPetrol,
            petrolStations = overlay.petrol.stations,
            petrolPlan = overlay.petrol.plan,
            petrolLoading = overlay.petrol.loading,
            petrolDetails = overlay.petrol.details,
            petrolDetailsLoading = overlay.petrol.detailsLoading,
            petrolMessage = overlay.petrol.message,
            tankCapacityLiters = core.fuel.tank,
            fuelRemainingLiters = core.fuel.remaining,
            fuelConsumption = core.fuel.consumption,
            fuelRangeSummary = fuelService.rangeSummary,
            isLowFuel = fuelService.isLowFuel,
            preferredBrands = core.fuel.brands,
            preferredOctanes = core.fuel.octanes,
            lastLatitude = core.ride.lastLocation?.latitude,
            lastLongitude = core.ride.lastLocation?.longitude,
            lastBearing = core.ride.lastLocation?.bearing ?: 0f,
            lastSpeedMps = core.ride.lastLocation?.speed ?: 0f
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RideTrackerUiState()
    )

    init {
        navigationService.onRouteApplied = { coordinates, travelTime ->
            routeWeatherService.refreshForRoute(coordinates, travelTime)
        }
        navigationService.onRouteCleared = routeWeatherService::clear
        startDashboardGps()
        viewModelScope.launch {
            locationRepository.lastLocation.collect { location ->
                location?.let { navigationService.updateOrigin(it.latitude, it.longitude) }
            }
        }
        viewModelScope.launch {
            observeRideSessionUseCase().collect { session ->
                if (session.isActive) {
                    fuelService.updateConsumedDistance(session.stats.distanceKm.toDouble())
                }
            }
        }
    }

    private fun startDashboardGps() {
        if (dashboardLocationJob?.isActive == true) return
        dashboardLocationJob = viewModelScope.launch {
            try {
                locationRepository.getLocationFlow().collect { location ->
                    if (location.hasAccuracy()) dashboardGpsAccuracy.value = location.accuracy
                }
            } catch (e: Exception) {
                AppLogger.w(AppLogger.Category.UI, "Dashboard GPS failed", e)
            }
        }
    }

    fun startRide() {
        if (uiState.value.isTracking) return
        fuelService.resetRideConsumption()
        startRideUseCase()
    }

    fun stopRide() {
        if (!uiState.value.isTracking) return
        val result = stopRideUseCase()
        if (!result.saved) {
            discardBanner.value = "Ride too short — not saved"
            viewModelScope.launch {
                delay(2_500)
                discardBanner.value = null
            }
        }
    }

    fun togglePause() {
        val state = uiState.value
        if (!state.isTracking) return
        if (state.isPaused) resumeRideUseCase() else pauseRideUseCase()
    }

    fun showDestinationSearch() { showDestinationSearch.value = true }
    fun dismissDestinationSearch() { showDestinationSearch.value = false }
    fun showFuelSettings() { showFuelSettings.value = true }
    fun dismissFuelSettings() { showFuelSettings.value = false }
    fun showRouteWeather() { showRouteWeather.value = true }
    fun dismissRouteWeather() { showRouteWeather.value = false }

    fun showPetrolStations() {
        showPetrolStations.value = true
        refreshPetrolStations()
    }

    fun dismissPetrolStations() {
        showPetrolStations.value = false
        petrolSearchJob?.cancel()
        petrolLoading.value = false
        petrolDetails.value = null
        petrolDetailsLoading.value = false
    }

    fun selectPetrolStation(station: PetrolStationRecommendation) {
        navigationService.setDestination(station.latitude, station.longitude, station.name)
        showPetrolStations.value = false
        petrolDetails.value = null
        petrolMessage.value = "Navigating to ${station.name}"
        viewModelScope.launch {
            delay(2_500)
            petrolMessage.value = null
        }
    }

    fun loadPetrolDetails(station: PetrolStationRecommendation) {
        viewModelScope.launch {
            petrolDetailsLoading.value = true
            petrolDetails.value = null
            petrolDetails.value = petrolStationFinder.fetchGoogleDetails(
                placeId = station.googlePlaceId,
                latitude = station.latitude,
                longitude = station.longitude
            )
            petrolDetailsLoading.value = false
        }
    }

    fun clearPetrolDetails() {
        petrolDetails.value = null
        petrolDetailsLoading.value = false
    }

    fun onNavigationQueryChange(query: String) = navigationService.updateSearchQuery(query)
    fun selectNavigationResult(result: NavigationSearchResult) {
        navigationService.selectSearchResult(result)
        showDestinationSearch.value = false
    }
    fun clearNavigation() = navigationService.clear()
    fun openNavigationInMaps() = navigationService.openInGoogleMaps()
    fun toggleNavigationVoice() = navigationService.toggleVoice()
    fun fuelService(): FuelService = fuelService
    fun petrolPreferences(): PetrolPreferences = petrolPreferences

    private fun refreshPetrolStations() {
        petrolSearchJob?.cancel()
        petrolSearchJob = viewModelScope.launch {
            petrolLoading.value = true
            petrolStations.value = emptyList()
            petrolPlan.value = null
            val location = locationRepository.lastLocation.value
                ?: uiState.value.lastLatitude?.let { lat ->
                    uiState.value.lastLongitude?.let { lng ->
                        android.location.Location("manual").apply {
                            latitude = lat
                            longitude = lng
                        }
                    }
                }
            if (location == null) {
                petrolLoading.value = false
                petrolMessage.value = "Waiting for GPS…"
                return@launch
            }
            val speedKmh = uiState.value.stats.speed.toDouble().takeIf { it > 0 }
                ?: (location.speed * 3.6)
            val course = location.bearing.takeIf { location.hasBearing() && it >= 0f }
            val result = petrolStationFinder.search(
                latitude = location.latitude,
                longitude = location.longitude,
                preferences = petrolPreferences,
                speedKmh = speedKmh,
                courseDegrees = course
            )
            petrolPlan.value = result.plan
            petrolStations.value = result.stations
            petrolLoading.value = false
            if (result.stations.isEmpty()) {
                petrolMessage.value = "No petrol stations found nearby"
                delay(2_500)
                petrolMessage.value = null
            }
        }
    }

    private data class RideInputs(
        val session: RideSessionState,
        val routeCoordinates: List<RouteCoordinate>,
        val navigation: com.odys.mototriptracker.data.navigation.NavigationState,
        val weather: com.odys.mototriptracker.data.weather.RouteWeatherState,
        val lastLocation: android.location.Location?
    )

    private data class FuelPrefs(
        val tank: Double,
        val remaining: Double,
        val consumption: Double,
        val brands: List<String>,
        val octanes: Set<Int>
    )

    private data class CoreInputs(
        val ride: RideInputs,
        val fuel: FuelPrefs,
        val dashAccuracy: Float?
    )

    private data class SheetFlags(
        val showSearch: Boolean,
        val showFuel: Boolean,
        val showWeather: Boolean,
        val showPetrol: Boolean
    )

    private data class PetrolExtras(
        val details: GooglePetrolDetails?,
        val detailsLoading: Boolean,
        val banner: String?,
        val message: String?
    )

    private data class PetrolUi(
        val stations: List<RankedPetrolStation>,
        val plan: PetrolSearchPlan?,
        val loading: Boolean,
        val details: GooglePetrolDetails?,
        val detailsLoading: Boolean,
        val banner: String?,
        val message: String?
    )

    private data class OverlayInputs(
        val sheets: SheetFlags,
        val petrol: PetrolUi
    )
}
