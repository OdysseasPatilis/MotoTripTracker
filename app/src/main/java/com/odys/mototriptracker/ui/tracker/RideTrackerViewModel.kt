package com.odys.mototriptracker.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.domain.usecase.ObserveRideSessionUseCase
import com.odys.mototriptracker.domain.usecase.PauseRideUseCase
import com.odys.mototriptracker.domain.usecase.ResumeRideUseCase
import com.odys.mototriptracker.domain.usecase.StartRideUseCase
import com.odys.mototriptracker.domain.usecase.StopRideUseCase
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RideTrackerViewModel @Inject constructor(
    observeRideSessionUseCase: ObserveRideSessionUseCase,
    private val startRideUseCase: StartRideUseCase,
    private val stopRideUseCase: StopRideUseCase,
    private val pauseRideUseCase: PauseRideUseCase,
    private val resumeRideUseCase: ResumeRideUseCase
) : ViewModel() {

    val uiState: StateFlow<RideTrackerUiState> = observeRideSessionUseCase()
        .map { session ->
            RideTrackerUiState(
                stats = session.stats,
                isTracking = session.isActive,
                isPaused = session.isPaused
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RideTrackerUiState()
        )

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
