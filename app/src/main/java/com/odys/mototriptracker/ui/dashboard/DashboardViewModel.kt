package com.odys.mototriptracker.ui.dashboard

import androidx.lifecycle.ViewModel
import com.odys.mototriptracker.data.trip.TripRepository
import com.odys.mototriptracker.domain.TripManager
import com.odys.mototriptracker.domain.TripStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DashboardViewModel(
    private val tripManager: TripManager,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _tripStats = MutableStateFlow(TripStats())
    val tripStats: StateFlow<TripStats> = _tripStats

    /*fun loadTrips() {
        val trips = tripRepository.getAllTrips()
        // Update UI with last trip or summary
    }*/
}