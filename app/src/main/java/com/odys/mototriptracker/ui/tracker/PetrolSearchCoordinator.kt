package com.odys.mototriptracker.ui.tracker

import com.odys.mototriptracker.application.GooglePetrolDetails
import com.odys.mototriptracker.application.PetrolSearchPlan
import com.odys.mototriptracker.application.PetrolStationRecommendation
import com.odys.mototriptracker.application.RankedPetrolStation
import com.odys.mototriptracker.application.RideTrackerFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Owns petrol-sheet visibility, search, and station-details state for the ride tracker. */
class PetrolSearchCoordinator @Inject constructor(
    private val facade: RideTrackerFacade,
) {
    private val _showSheet = MutableStateFlow(false)
    private val _stations = MutableStateFlow<List<RankedPetrolStation>>(emptyList())
    private val _plan = MutableStateFlow<PetrolSearchPlan?>(null)
    private val _loading = MutableStateFlow(false)
    private val _details = MutableStateFlow<GooglePetrolDetails?>(null)
    private val _detailsLoading = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null

    val showSheet: StateFlow<Boolean> = _showSheet.asStateFlow()
    val stations: StateFlow<List<RankedPetrolStation>> = _stations.asStateFlow()
    val plan: StateFlow<PetrolSearchPlan?> = _plan.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val details: StateFlow<GooglePetrolDetails?> = _details.asStateFlow()
    val detailsLoading: StateFlow<Boolean> = _detailsLoading.asStateFlow()
    val message: StateFlow<String?> = _message.asStateFlow()

    fun show(
        scope: CoroutineScope,
        speedKmh: () -> Double,
        fallbackLatLng: () -> Pair<Double, Double>?,
    ) {
        _showSheet.value = true
        refresh(scope, speedKmh, fallbackLatLng)
    }

    fun dismiss() {
        _showSheet.value = false
        searchJob?.cancel()
        _loading.value = false
        _details.value = null
        _detailsLoading.value = false
    }

    fun selectStation(
        scope: CoroutineScope,
        station: PetrolStationRecommendation,
        onNavigate: (PetrolStationRecommendation) -> Unit,
    ) {
        onNavigate(station)
        _showSheet.value = false
        _details.value = null
        _message.value = "Previewing route to ${station.name}"
        scope.launch {
            delay(2_500)
            _message.value = null
        }
    }

    fun loadDetails(scope: CoroutineScope, station: PetrolStationRecommendation) {
        scope.launch {
            _detailsLoading.value = true
            _details.value = null
            _details.value = facade.fetchPetrolDetails(
                placeId = station.googlePlaceId,
                latitude = station.latitude,
                longitude = station.longitude,
            )
            _detailsLoading.value = false
        }
    }

    fun clearDetails() {
        _details.value = null
        _detailsLoading.value = false
    }

    private fun refresh(
        scope: CoroutineScope,
        speedKmh: () -> Double,
        fallbackLatLng: () -> Pair<Double, Double>?,
    ) {
        searchJob?.cancel()
        searchJob = scope.launch {
            _loading.value = true
            _stations.value = emptyList()
            _plan.value = null
            val latLng = facade.currentLatLng() ?: fallbackLatLng()
            if (latLng == null) {
                _loading.value = false
                _message.value = "Waiting for GPS…"
                return@launch
            }
            val (latitude, longitude) = latLng
            val course = facade.currentCourseDegrees()
            val result = facade.searchPetrolStations(
                latitude = latitude,
                longitude = longitude,
                speedKmh = speedKmh().takeIf { it > 0 } ?: facade.currentSpeedKmh(),
                courseDegrees = course,
            )
            _plan.value = result.plan
            _stations.value = result.stations
            _loading.value = false
            if (result.stations.isEmpty()) {
                _message.value = "No petrol stations found nearby"
                delay(2_500)
                _message.value = null
            }
        }
    }
}
