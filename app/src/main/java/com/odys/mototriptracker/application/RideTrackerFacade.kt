package com.odys.mototriptracker.application

import android.location.Location
import com.odys.mototriptracker.data.camera.TrafficCameraService
import com.odys.mototriptracker.data.fuel.FuelService
import com.odys.mototriptracker.data.location.LocationRepository
import com.odys.mototriptracker.data.navigation.DestinationSearchHistory
import com.odys.mototriptracker.data.navigation.NavigationService
import com.odys.mototriptracker.data.petrol.PetrolPreferences
import com.odys.mototriptracker.data.petrol.PetrolStationFinder
import com.odys.mototriptracker.data.weather.RouteWeatherService
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.domain.usecase.ObserveRouteCoordinatesUseCase
import com.odys.mototriptracker.domain.usecase.PauseRideUseCase
import com.odys.mototriptracker.domain.usecase.ResumeRideUseCase
import com.odys.mototriptracker.domain.usecase.StartRideUseCase
import com.odys.mototriptracker.domain.usecase.StopRideResult
import com.odys.mototriptracker.domain.usecase.StopRideUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application façade for the live ride tracker: wires data services and ride
 * use cases so the UI ViewModel / coordinators do not depend on them directly.
 */
@Singleton
class RideTrackerFacade @Inject constructor(
    private val observeRouteCoordinates: ObserveRouteCoordinatesUseCase,
    private val navigationService: NavigationService,
    private val destinationHistory: DestinationSearchHistory,
    private val trafficCameraService: TrafficCameraService,
    private val routeWeatherService: RouteWeatherService,
    private val fuelService: FuelService,
    private val petrolPreferences: PetrolPreferences,
    private val petrolStationFinder: PetrolStationFinder,
    private val locationRepository: LocationRepository,
    private val startRideUseCase: StartRideUseCase,
    private val stopRideUseCase: StopRideUseCase,
    private val pauseRideUseCase: PauseRideUseCase,
    private val resumeRideUseCase: ResumeRideUseCase,
) {
    init {
        navigationService.onRouteApplied = { coordinates, travelTime ->
            routeWeatherService.refreshForRoute(coordinates, travelTime)
        }
        navigationService.onRouteCleared = routeWeatherService::clear
    }

    val routeCoordinates: StateFlow<List<RouteCoordinate>>
        get() = observeRouteCoordinates()

    val navigation: StateFlow<NavigationState>
        get() = navigationService.state

    val weather: StateFlow<RouteWeatherState>
        get() = routeWeatherService.state

    val lastLocation: StateFlow<Location?>
        get() = locationRepository.lastLocation

    val locationUpdates: Flow<Location>
        get() = locationRepository.getLocationFlow()

    val tankCapacityLiters: StateFlow<Double>
        get() = fuelService.tankCapacityLiters

    val fuelRemainingLiters: StateFlow<Double>
        get() = fuelService.fuelRemainingLiters

    val consumptionLPer100Km: StateFlow<Double>
        get() = fuelService.consumptionLPer100Km

    val preferredBrands: StateFlow<List<String>>
        get() = petrolPreferences.preferredBrands

    val preferredOctanes: StateFlow<Set<Int>>
        get() = petrolPreferences.preferredOctanes

    val mapCameras: StateFlow<List<TrafficCamera>>
        get() = trafficCameraService.mapCameras

    val trafficCameraAlert: StateFlow<TrafficCameraAlert?>
        get() = trafficCameraService.activeAlert

    val trafficCameraDownloadStatus: StateFlow<TrafficCameraPackDownloadStatus>
        get() = trafficCameraService.downloadStatus

    val fuelRangeSummary: String
        get() = fuelService.rangeSummary

    val isLowFuel: Boolean
        get() = fuelService.isLowFuel

    val petrolBrandCatalog: List<String>
        get() = PetrolPreferences.CATALOG

    fun startRide() {
        fuelService.resetRideConsumption()
        trafficCameraService.reset()
        startRideUseCase()
    }

    fun stopRide(): StopRideResult {
        val result = stopRideUseCase()
        trafficCameraService.reset()
        locationRepository.lastLocation.value?.let { location ->
            trafficCameraService.refresh(location, alertsEnabled = false)
        }
        return result
    }

    fun pauseRide() = pauseRideUseCase()
    fun resumeRide() = resumeRideUseCase()

    fun updateFuelConsumedDistance(distanceKm: Double) {
        fuelService.updateConsumedDistance(distanceKm)
    }

    fun updateNavigationOrigin(latitude: Double, longitude: Double) {
        navigationService.updateOrigin(latitude, longitude)
    }

    fun refreshTrafficCameras(location: Location, alertsEnabled: Boolean) {
        trafficCameraService.refresh(location, alertsEnabled = alertsEnabled)
    }

    fun updateVisibleMapRegion(
        centerLat: Double,
        centerLng: Double,
        latDelta: Double,
        lngDelta: Double,
        fetchRemote: Boolean,
    ) {
        trafficCameraService.updateVisibleMapRegion(
            centerLatitude = centerLat,
            centerLongitude = centerLng,
            latitudeDelta = latDelta,
            longitudeDelta = lngDelta,
            fetchRemote = fetchRemote,
        )
    }

    fun setDestination(latitude: Double, longitude: Double, name: String, subtitle: String) {
        navigationService.setDestination(
            latitude = latitude,
            longitude = longitude,
            name = name,
            subtitle = subtitle,
        )
    }

    suspend fun resolveMapPlace(
        placeId: String,
        fallbackName: String,
        latitude: Double,
        longitude: Double,
    ): PickedMapPlace = navigationService.resolveMapPlace(
        placeId = placeId,
        fallbackName = fallbackName,
        latitude = latitude,
        longitude = longitude,
    )

    fun updateSearchQuery(query: String) = navigationService.updateSearchQuery(query)

    fun selectSearchResult(result: NavigationSearchResult) =
        navigationService.selectSearchResult(result)

    fun selectHistoryEntry(entry: DestinationHistoryEntry) =
        navigationService.selectHistoryEntry(entry)

    fun removeHistoryDestination(id: String) = destinationHistory.remove(id)

    fun destinationHistoryEntries(): List<DestinationHistoryEntry> = destinationHistory.all()

    fun clearNavigation() = navigationService.clear()
    fun confirmStartNavigation() = navigationService.confirmStartNavigation()
    fun cancelNavigationPreview() = navigationService.cancelPreview()
    fun selectPreviewRoute(id: String) = navigationService.selectPreviewRoute(id)
    fun dismissTimingResult() = navigationService.dismissTimingResult()
    fun openNavigationInMaps() = navigationService.openInGoogleMaps()
    fun toggleNavigationVoice() = navigationService.toggleVoice()

    fun toggleFuelBrand(brand: String) = petrolPreferences.toggleBrand(brand)
    fun toggleFuelOctane(octane: Int) = petrolPreferences.toggleOctane(octane)
    fun fillUpFuel() = fuelService.fillUp()
    fun isPreferredBrand(rawBrand: String?): Boolean = petrolPreferences.isPreferredBrand(rawBrand)

    fun saveFuelSettings(
        capacityLiters: Double?,
        remainingLiters: Double?,
        consumptionLPer100Km: Double?,
    ) {
        capacityLiters?.let(fuelService::setTankCapacityLiters)
        remainingLiters?.let(fuelService::setFuelRemainingLiters)
        consumptionLPer100Km?.let(fuelService::setConsumptionLPer100Km)
    }

    fun currentLatLng(): Pair<Double, Double>? {
        val location = locationRepository.lastLocation.value ?: return null
        return location.latitude to location.longitude
    }

    fun currentCourseDegrees(): Float? {
        val location = locationRepository.lastLocation.value ?: return null
        return location.bearing.takeIf { location.hasBearing() && it >= 0f }
    }

    fun currentSpeedKmh(): Double {
        val location = locationRepository.lastLocation.value ?: return 0.0
        return (location.speed * 3.6).toDouble()
    }

    suspend fun searchPetrolStations(
        latitude: Double,
        longitude: Double,
        speedKmh: Double,
        courseDegrees: Float?,
    ): PetrolSearchResult = petrolStationFinder.search(
        latitude = latitude,
        longitude = longitude,
        preferences = petrolPreferences,
        speedKmh = speedKmh,
        courseDegrees = courseDegrees,
    )

    suspend fun fetchPetrolDetails(
        placeId: String?,
        latitude: Double,
        longitude: Double,
    ): GooglePetrolDetails? = petrolStationFinder.fetchGoogleDetails(
        placeId = placeId,
        latitude = latitude,
        longitude = longitude,
    )
}
