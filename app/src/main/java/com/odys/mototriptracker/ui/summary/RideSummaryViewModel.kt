package com.odys.mototriptracker.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.data.trip.TripRepository
import com.odys.mototriptracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RideSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val tripId: Long = checkNotNull(savedStateHandle[Routes.TRIP_ID_ARG])

    private val _uiState = MutableStateFlow(RideSummaryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadTrip()
    }

    private fun loadTrip() {
        viewModelScope.launch(Dispatchers.IO) {
            val trip = tripRepository.getTrip(tripId)
            _uiState.value = if (trip == null) {
                RideSummaryUiState(isLoading = false, notFound = true)
            } else {
                RideSummaryUiState(trip = trip, isLoading = false)
            }
        }
    }

    fun deleteTrip() {
        viewModelScope.launch(Dispatchers.IO) {
            tripRepository.deleteTrip(tripId)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }
}
