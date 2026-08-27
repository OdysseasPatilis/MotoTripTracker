package com.odys.mototriptracker.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.data.location.LocationRepository
import com.odys.mototriptracker.domain.GpsQuality
import com.odys.mototriptracker.domain.usecase.ObserveRideSessionUseCase
import com.odys.mototriptracker.domain.usecase.PauseRideUseCase
import com.odys.mototriptracker.domain.usecase.ResumeRideUseCase
import com.odys.mototriptracker.domain.usecase.StartRideUseCase
import com.odys.mototriptracker.domain.usecase.StopRideUseCase
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    private val locationRepository: LocationRepository,
    private val startRideUseCase: StartRideUseCase,
    private val stopRideUseCase: StopRideUseCase,
    private val pauseRideUseCase: PauseRideUseCase,
    private val resumeRideUseCase: ResumeRideUseCase
) : ViewModel() {

    private val dashboardGpsAccuracy = MutableStateFlow<Float?>(null)
    private var dashboardLocationJob: Job? = null

    val uiState: StateFlow<RideTrackerUiState> = combine(
        observeRideSessionUseCase(),
        dashboardGpsAccuracy,
        locationRepository.lastLocation
    ) { session, dashAccuracy, lastLocation ->
        // Prefer live fused location so the indicator updates when idle / paused (iOS parity).
        val liveAccuracy = lastLocation?.takeIf { it.hasAccuracy() && it.accuracy >= 0f }?.accuracy
            ?: dashAccuracy
            ?: session.stats.gpsAccuracyMeters
        val liveQuality = GpsQuality.fromAccuracyMeters(liveAccuracy)

        RideTrackerUiState(
            stats = session.stats.copy(
                gpsAccuracyMeters = liveAccuracy,
                gpsQuality = liveQuality
            ),
            isTracking = session.isActive,
            isPaused = session.isPaused
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RideTrackerUiState()
    )

    init {
        startDashboardGps()
    }

    /** Keep a GPS fix warm while the tracker screen is visible (idle or after stop). */
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
        if (uiState.value.isTracking) {
            AppLogger.d(AppLogger.Category.UI, "startRide ignored — already tracking")
            return
        }
        AppLogger.i(AppLogger.Category.UI, "startRide requested")
        startRideUseCase()
    }

    fun stopRide() {
        if (!uiState.value.isTracking) {
            AppLogger.d(AppLogger.Category.UI, "stopRide ignored — not tracking")
            return
        }

        AppLogger.i(AppLogger.Category.UI, "stopRide requested")
        val result = stopRideUseCase()
        if (result.isTooShort) {
            AppLogger.w(
                AppLogger.Category.UI,
                "Ride too short (${result.distanceMeters}m < ${StopRideUseCase.MIN_DISTANCE_METERS}m)"
            )
        }
    }

    fun togglePause() {
        val state = uiState.value
        if (!state.isTracking) {
            AppLogger.d(AppLogger.Category.UI, "togglePause ignored — not tracking")
            return
        }

        if (state.isPaused) {
            AppLogger.i(AppLogger.Category.UI, "resumeRide requested")
            resumeRideUseCase()
        } else {
            AppLogger.i(AppLogger.Category.UI, "pauseRide requested")
            pauseRideUseCase()
        }
    }
}
