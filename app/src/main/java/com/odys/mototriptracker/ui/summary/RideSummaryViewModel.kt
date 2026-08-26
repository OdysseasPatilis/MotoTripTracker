package com.odys.mototriptracker.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.domain.RideMomentsCalculator
import com.odys.mototriptracker.domain.usecase.DeleteTripUseCase
import com.odys.mototriptracker.domain.usecase.GetTripRouteUseCase
import com.odys.mototriptracker.domain.usecase.ToggleFavoriteUseCase
import com.odys.mototriptracker.domain.usecase.UpdateTripTitleUseCase
import com.odys.mototriptracker.ui.navigation.Routes
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RideSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTripRouteUseCase: GetTripRouteUseCase,
    private val deleteTripUseCase: DeleteTripUseCase,
    private val updateTripTitleUseCase: UpdateTripTitleUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {

    private val tripId: Long = checkNotNull(savedStateHandle[Routes.TRIP_ID_ARG])

    private val _uiState = MutableStateFlow(RideSummaryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadTrip()
    }

    private fun loadTrip() {
        viewModelScope.launch(Dispatchers.IO) {
            val details = getTripRouteUseCase(tripId)
            _uiState.value = if (details == null) {
                RideSummaryUiState(isLoading = false, notFound = true)
            } else {
                val moments = RideMomentsCalculator.calculate(details.trip, details.routePoints)
                AppLogger.i(
                    AppLogger.Category.UI,
                    "Summary loaded id=$tripId moments=${moments.moments.size} points=${details.routePoints.size}"
                )
                RideSummaryUiState(
                    trip = details.trip,
                    routePoints = details.routePoints,
                    moments = moments,
                    isLoading = false
                )
            }
        }
    }

    fun renameTrip(title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updateTripTitleUseCase(tripId, title)
            reloadTripMeta()
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch(Dispatchers.IO) {
            toggleFavoriteUseCase(tripId)
            reloadTripMeta()
        }
    }

    private fun reloadTripMeta() {
        val details = getTripRouteUseCase(tripId) ?: return
        val moments = RideMomentsCalculator.calculate(details.trip, details.routePoints)
        _uiState.value = RideSummaryUiState(
            trip = details.trip,
            routePoints = details.routePoints,
            moments = moments,
            isLoading = false
        )
    }

    fun deleteTrip() {
        viewModelScope.launch(Dispatchers.IO) {
            deleteTripUseCase(tripId)
            AppLogger.i(AppLogger.Category.UI, "Delete requested for trip id=$tripId")
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }
}
