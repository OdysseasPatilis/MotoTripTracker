package com.odys.mototriptracker.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.domain.usecase.ObserveRideSessionUseCase
import com.odys.mototriptracker.domain.usecase.PauseRideUseCase
import com.odys.mototriptracker.domain.usecase.ResumeRideUseCase
import com.odys.mototriptracker.domain.usecase.StartRideUseCase
import com.odys.mototriptracker.domain.usecase.StopRideUseCase
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
        if (uiState.value.isTracking) return
        startRideUseCase()
    }

    fun stopRide() {
        if (!uiState.value.isTracking) return

        val result = stopRideUseCase()
        if (result.isTooShort) {
            println("Ride was less than 50 meters. Too short!")
        }
    }

    fun togglePause() {
        val state = uiState.value
        if (!state.isTracking) return

        if (state.isPaused) {
            resumeRideUseCase()
        } else {
            pauseRideUseCase()
        }
    }
}
