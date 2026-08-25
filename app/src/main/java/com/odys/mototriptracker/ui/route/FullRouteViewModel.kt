package com.odys.mototriptracker.ui.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.data.trip.TripRepository
import com.odys.mototriptracker.ui.mapper.toRidePoint
import com.odys.mototriptracker.ui.mapper.toWaypoint
import com.odys.mototriptracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FullRouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val tripId: Long = checkNotNull(savedStateHandle[Routes.TRIP_ID_ARG])

    private val _uiState = MutableStateFlow(FullRouteUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadRoute()
    }

    private fun loadRoute() {
        viewModelScope.launch(Dispatchers.IO) {
            val trip = tripRepository.getTrip(tripId)
            if (trip == null) {
                _uiState.value = FullRouteUiState(isLoading = false, notFound = true)
                return@launch
            }

            val ridePoints = tripRepository.getRoutePointsForMap(tripId).map { it.toRidePoint() }
            val waypoints = tripRepository.getWaypointsForTrip(tripId).map { it.toWaypoint() }

            _uiState.value = FullRouteUiState(
                trip = trip,
                ridePoints = ridePoints,
                waypoints = waypoints,
                isLoading = false
            )
        }
    }
}
