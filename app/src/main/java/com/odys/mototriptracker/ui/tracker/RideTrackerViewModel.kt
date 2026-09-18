package com.odys.mototriptracker.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.application.RideTrackerFacade
import com.odys.mototriptracker.data.navigation.DestinationHistoryEntry
import com.odys.mototriptracker.data.navigation.NavigationSearchResult
import com.odys.mototriptracker.data.navigation.NavigationState
import com.odys.mototriptracker.data.petrol.GooglePetrolDetails
import com.odys.mototriptracker.data.petrol.PetrolPreferences
import com.odys.mototriptracker.data.petrol.PetrolSearchPlan
import com.odys.mototriptracker.data.petrol.PetrolStationRecommendation
import com.odys.mototriptracker.data.petrol.RankedPetrolStation
import com.odys.mototriptracker.data.weather.RouteWeatherState
import com.odys.mototriptracker.domain.GpsQuality
import com.odys.mototriptracker.domain.RideSessionState
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.domain.usecase.ObserveRideSessionUseCase
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
    private val facade: RideTrackerFacade,
    private val petrolSearch: PetrolSearchCoordinator,
    private val mapPlace: MapPlaceCoordinator,
) : ViewModel() {
    private val observeRideSession = observeRideSessionUseCase

    private val dashboardGpsAccuracy = MutableStateFlow<Float?>(null)
    private val showDestinationSearch = MutableStateFlow(false)
    private val showFuelSettings = MutableStateFlow(false)
    private val showRouteWeather = MutableStateFlow(false)
    private val discardBanner = MutableStateFlow<String?>(null)
    private var dashboardLocationJob: Job? = null

    private val rideInputs = combine(
        observeRideSession(),
        facade.routeCoordinates,
        facade.navigation,
        facade.weather,
        facade.lastLocation,
    ) { session, routeCoordinates, navigation, weather, lastLocation ->
        RideInputs(session, routeCoordinates, navigation, weather, lastLocation)
    }

    private val fuelInputs = combine(
        facade.tankCapacityLiters,
        facade.fuelRemainingLiters,
        facade.consumptionLPer100Km,
        facade.preferredBrands,
        facade.preferredOctanes,
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
        petrolSearch.showSheet,
    ) { search, fuel, weather, petrol ->
        SheetFlags(search, fuel, weather, petrol)
    }

    private val petrolUiCore = combine(
        petrolSearch.stations,
        petrolSearch.plan,
        petrolSearch.loading,
    ) { stations, plan, loading ->
        Triple(stations, plan, loading)
    }

    private val petrolUiExtras = combine(
        petrolSearch.details,
        petrolSearch.detailsLoading,
        discardBanner,
        petrolSearch.message,
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
            message = extras.message,
        )
    }

    private val overlayInputs = combine(sheetFlags, petrolUi) { sheets, petrol ->
        OverlayInputs(sheets, petrol)
    }

    private val cameraInputs = combine(
        facade.mapCameras,
        facade.trafficCameraAlert,
        facade.trafficCameraDownloadStatus,
    ) { mapCams, alert, download ->
        Triple(mapCams, alert, download)
    }

    val uiState: StateFlow<RideTrackerUiState> = combine(
        coreInputs,
        overlayInputs,
        cameraInputs,
        mapPlace.selected,
    ) { core, overlay, cameras, pickedPlace ->
        val liveAccuracy = core.ride.lastLocation?.takeIf { it.hasAccuracy() && it.accuracy >= 0f }?.accuracy
            ?: core.dashAccuracy
            ?: core.ride.session.stats.gpsAccuracyMeters

        RideTrackerUiState(
            stats = core.ride.session.stats.copy(
                gpsAccuracyMeters = liveAccuracy,
                gpsQuality = GpsQuality.fromAccuracyMeters(liveAccuracy),
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
            fuelRangeSummary = facade.fuelRangeSummary,
            isLowFuel = facade.isLowFuel,
            preferredBrands = core.fuel.brands,
            preferredOctanes = core.fuel.octanes,
            lastLatitude = core.ride.lastLocation?.latitude,
            lastLongitude = core.ride.lastLocation?.longitude,
            lastBearing = core.ride.lastLocation?.bearing ?: 0f,
            lastSpeedMps = core.ride.lastLocation?.speed ?: 0f,
            nearbyTrafficCameras = cameras.first,
            trafficCameraAlert = cameras.second,
            trafficCameraDownloadStatus = cameras.third,
            selectedMapPlace = pickedPlace,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RideTrackerUiState(),
    )

    init {
        startDashboardGps()
        viewModelScope.launch {
            facade.lastLocation.collect { location ->
                location?.let { facade.updateNavigationOrigin(it.latitude, it.longitude) }
            }
        }
        viewModelScope.launch {
            observeRideSession().collect { session ->
                if (session.isActive) {
                    facade.updateFuelConsumedDistance(session.stats.distanceKm.toDouble())
                }
            }
        }
    }

    private fun startDashboardGps() {
        if (dashboardLocationJob?.isActive == true) return
        dashboardLocationJob = viewModelScope.launch {
            try {
                facade.locationUpdates.collect { location ->
                    if (location.hasAccuracy()) dashboardGpsAccuracy.value = location.accuracy
                    val session = observeRideSession().value
                    if (!session.isActive) {
                        facade.refreshTrafficCameras(location, alertsEnabled = false)
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(AppLogger.Category.UI, "Dashboard GPS failed", e)
            }
        }
    }

    fun startRide() {
        if (uiState.value.isTracking) return
        facade.startRide()
    }

    fun stopRide() {
        if (!uiState.value.isTracking) return
        val result = facade.stopRide()
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
        if (state.isPaused) facade.resumeRide() else facade.pauseRide()
    }

    fun showDestinationSearch() { showDestinationSearch.value = true }
    fun dismissDestinationSearch() { showDestinationSearch.value = false }
    fun showFuelSettings() { showFuelSettings.value = true }
    fun dismissFuelSettings() { showFuelSettings.value = false }
    fun showRouteWeather() { showRouteWeather.value = true }
    fun dismissRouteWeather() { showRouteWeather.value = false }

    fun showPetrolStations() {
        petrolSearch.show(
            scope = viewModelScope,
            speedKmh = { uiState.value.stats.speed.toDouble() },
            fallbackLatLng = {
                val s = uiState.value
                val lat = s.lastLatitude ?: return@show null
                val lng = s.lastLongitude ?: return@show null
                lat to lng
            },
        )
    }

    fun dismissPetrolStations() = petrolSearch.dismiss()

    fun selectPetrolStation(station: PetrolStationRecommendation) {
        petrolSearch.selectStation(viewModelScope, station) {
            facade.setDestination(
                latitude = it.latitude,
                longitude = it.longitude,
                name = it.name,
                subtitle = "Petrol station",
            )
        }
    }

    fun loadPetrolDetails(station: PetrolStationRecommendation) =
        petrolSearch.loadDetails(viewModelScope, station)

    fun clearPetrolDetails() = petrolSearch.clearDetails()

    fun onNavigationQueryChange(query: String) = facade.updateSearchQuery(query)

    fun selectNavigationResult(result: NavigationSearchResult) {
        facade.selectSearchResult(result)
        showDestinationSearch.value = false
    }

    fun selectHistoryDestination(entry: DestinationHistoryEntry) {
        facade.selectHistoryEntry(entry)
        showDestinationSearch.value = false
    }

    fun removeHistoryDestination(id: String) = facade.removeHistoryDestination(id)
    fun destinationHistoryEntries(): List<DestinationHistoryEntry> = facade.destinationHistoryEntries()

    fun updateVisibleMapRegion(
        centerLat: Double,
        centerLng: Double,
        latDelta: Double,
        lngDelta: Double,
        fetchRemote: Boolean,
    ) {
        facade.updateVisibleMapRegion(centerLat, centerLng, latDelta, lngDelta, fetchRemote)
    }

    fun onMapPoiClick(placeId: String, name: String, latitude: Double, longitude: Double) {
        mapPlace.onPoiClick(viewModelScope, placeId, name, latitude, longitude)
    }

    fun dismissMapPlace() = mapPlace.dismiss()
    fun goToSelectedMapPlace() = mapPlace.goToSelected()

    fun clearNavigation() = facade.clearNavigation()
    fun confirmStartNavigation() = facade.confirmStartNavigation()
    fun cancelNavigationPreview() = facade.cancelNavigationPreview()
    fun selectPreviewRoute(id: String) = facade.selectPreviewRoute(id)
    fun dismissTimingResult() = facade.dismissTimingResult()
    fun openNavigationInMaps() = facade.openNavigationInMaps()
    fun toggleNavigationVoice() = facade.toggleNavigationVoice()

    fun toggleFuelBrand(brand: String) = facade.toggleFuelBrand(brand)
    fun toggleFuelOctane(octane: Int) = facade.toggleFuelOctane(octane)
    fun fillUpFuel() = facade.fillUpFuel()

    fun saveFuelSettings(
        capacityLiters: Double?,
        remainingLiters: Double?,
        consumptionLPer100Km: Double?,
    ) {
        facade.saveFuelSettings(capacityLiters, remainingLiters, consumptionLPer100Km)
    }

    fun petrolPreferences(): PetrolPreferences = facade.petrolPreferences()

    private data class RideInputs(
        val session: RideSessionState,
        val routeCoordinates: List<RouteCoordinate>,
        val navigation: NavigationState,
        val weather: RouteWeatherState,
        val lastLocation: android.location.Location?,
    )

    private data class FuelPrefs(
        val tank: Double,
        val remaining: Double,
        val consumption: Double,
        val brands: List<String>,
        val octanes: Set<Int>,
    )

    private data class CoreInputs(
        val ride: RideInputs,
        val fuel: FuelPrefs,
        val dashAccuracy: Float?,
    )

    private data class SheetFlags(
        val showSearch: Boolean,
        val showFuel: Boolean,
        val showWeather: Boolean,
        val showPetrol: Boolean,
    )

    private data class PetrolExtras(
        val details: GooglePetrolDetails?,
        val detailsLoading: Boolean,
        val banner: String?,
        val message: String?,
    )

    private data class PetrolUi(
        val stations: List<RankedPetrolStation>,
        val plan: PetrolSearchPlan?,
        val loading: Boolean,
        val details: GooglePetrolDetails?,
        val detailsLoading: Boolean,
        val banner: String?,
        val message: String?,
    )

    private data class OverlayInputs(
        val sheets: SheetFlags,
        val petrol: PetrolUi,
    )
}
