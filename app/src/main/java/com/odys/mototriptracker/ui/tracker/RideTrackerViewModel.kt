package com.odys.mototriptracker.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.domain.usecase.ObserveTripStatsUseCase
import com.odys.mototriptracker.domain.usecase.StartRideUseCase
import com.odys.mototriptracker.domain.usecase.StopRideUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RideTrackerViewModel @Inject constructor(
    observeTripStatsUseCase: ObserveTripStatsUseCase,
    private val startRideUseCase: StartRideUseCase,
    private val stopRideUseCase: StopRideUseCase
) : ViewModel() {

    private val isTracking = MutableStateFlow(false)
    private val isPaused = MutableStateFlow(false)

    val uiState: StateFlow<RideTrackerUiState> = combine(
        observeTripStatsUseCase(),
        isTracking,
        isPaused
    ) { stats, tracking, paused ->
        RideTrackerUiState(
            stats = stats,
            isTracking = tracking,
            isPaused = paused
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RideTrackerUiState()
    )

    fun startRide() {
        if (isTracking.value) return
        isTracking.value = true
        isPaused.value = false
        startRideUseCase()
    }

    fun stopRide() {
        if (!isTracking.value) return
        isTracking.value = false
        isPaused.value = false

        val result = stopRideUseCase()
        if (result.isTooShort) {
            println("Ride was less than 50 meters. Too short!")
        }
    }

    fun pauseRide() {
        // Stub until pause/resume is implemented end-to-end.
        println("on pause")
    }
}
