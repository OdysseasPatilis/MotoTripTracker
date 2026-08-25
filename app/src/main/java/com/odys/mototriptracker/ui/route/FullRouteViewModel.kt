package com.odys.mototriptracker.ui.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.domain.usecase.GetTripRouteUseCase
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
    private val getTripRouteUseCase: GetTripRouteUseCase
) : ViewModel() {

    private val tripId: Long = checkNotNull(savedStateHandle[Routes.TRIP_ID_ARG])

    private val _uiState = MutableStateFlow(FullRouteUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadRoute()
    }

    private fun loadRoute() {
        viewModelScope.launch(Dispatchers.IO) {
            val details = getTripRouteUseCase(tripId)
            if (details == null) {
                _uiState.value = FullRouteUiState(isLoading = false, notFound = true)
                return@launch
            }

            _uiState.value = FullRouteUiState(
                trip = details.trip,
                ridePoints = details.routePoints.map { it.toRidePoint() },
                waypoints = details.waypoints.map { it.toWaypoint() },
                isLoading = false
            )
        }
    }
}
