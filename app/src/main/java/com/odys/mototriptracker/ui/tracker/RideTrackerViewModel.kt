package com.odys.mototriptracker.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.data.location.LocationRepository
import com.odys.mototriptracker.data.navigation.NavigationSearchResult
import com.odys.mototriptracker.data.navigation.NavigationService
import com.odys.mototriptracker.domain.GpsQuality
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
    private val locationRepository: LocationRepository,
    private val startRideUseCase: StartRideUseCase,
    private val stopRideUseCase: StopRideUseCase,
    private val pauseRideUseCase: PauseRideUseCase,
    private val resumeRideUseCase: ResumeRideUseCase
) : ViewModel() {

    private val dashboardGpsAccuracy = MutableStateFlow<Float?>(null)
    private val showDestinationSearch = MutableStateFlow(false)
    private val discardBanner = MutableStateFlow<String?>(null)
    private var dashboardLocationJob: Job? = null

    private val dashboardInputs = combine(
        observeRideSessionUseCase(),
        tripManager.routeCoordinates,
        navigationService.state,
        dashboardGpsAccuracy,
        locationRepository.lastLocation
    ) { session, routeCoordinates, navigation, dashAccuracy, lastLocation ->
        DashboardInputs(session, routeCoordinates, navigation, dashAccuracy, lastLocation)
    }

    val uiState: StateFlow<RideTrackerUiState> = combine(
        dashboardInputs,
        showDestinationSearch,
        discardBanner
    ) { inputs, showSearch, banner ->
        val liveAccuracy = inputs.lastLocation?.takeIf { it.hasAccuracy() && it.accuracy >= 0f }?.accuracy
            ?: inputs.dashAccuracy
            ?: inputs.session.stats.gpsAccuracyMeters
        val liveQuality = GpsQuality.fromAccuracyMeters(liveAccuracy)

        RideTrackerUiState(
            stats = inputs.session.stats.copy(
                gpsAccuracyMeters = liveAccuracy,
                gpsQuality = liveQuality
            ),
            isTracking = inputs.session.isActive,
            isPaused = inputs.session.isPaused,
            routeCoordinates = inputs.routeCoordinates,
            navigation = inputs.navigation,
            discardBanner = banner,
            showDestinationSearch = showSearch,
            lastLatitude = inputs.lastLocation?.latitude,
            lastLongitude = inputs.lastLocation?.longitude,
            lastBearing = inputs.lastLocation?.bearing ?: 0f,
            lastSpeedMps = inputs.lastLocation?.speed ?: 0f
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RideTrackerUiState()
    )

    init {
        startDashboardGps()
        viewModelScope.launch {
            locationRepository.lastLocation.collect { location ->
                location?.let {
                    navigationService.updateOrigin(it.latitude, it.longitude)
                }
            }
        }
    }

    private fun startDashboardGps() {
        if (dashboardLocationJob?.isActive == true) return
        dashboardLocationJob = viewModelScope.launch {
            try {
                locationRepository.getLocationFlow().collect { location ->
                    if (location.hasAccuracy()) {
                        dashboardGpsAccuracy.value = location.accuracy
                    }
                }
            } catch (t: Throwable) {
                AppLogger.e(AppLogger.Category.UI, "Dashboard GPS collection failed", t)
            }
        }
    }

    fun startRide() {
        if (uiState.value.isTracking) return
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

    fun showDestinationSearch() {
        showDestinationSearch.value = true
    }

    fun dismissDestinationSearch() {
        showDestinationSearch.value = false
    }

    fun onNavigationQueryChange(query: String) {
        navigationService.updateSearchQuery(query)
    }

    fun selectNavigationResult(result: NavigationSearchResult) {
        navigationService.selectSearchResult(result)
        showDestinationSearch.value = false
    }

    fun clearNavigation() {
        navigationService.clear()
    }

    fun openNavigationInMaps() {
        navigationService.openInGoogleMaps()
    }

    private data class DashboardInputs(
        val session: com.odys.mototriptracker.domain.RideSessionState,
        val routeCoordinates: List<com.odys.mototriptracker.domain.RouteCoordinate>,
        val navigation: com.odys.mototriptracker.data.navigation.NavigationState,
        val dashAccuracy: Float?,
        val lastLocation: android.location.Location?
    )
}
