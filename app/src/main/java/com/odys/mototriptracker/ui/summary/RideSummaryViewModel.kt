package com.odys.mototriptracker.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.domain.RideMomentsCalculator
import com.odys.mototriptracker.domain.usecase.DeleteTripUseCase
import com.odys.mototriptracker.domain.usecase.ExportGpxUseCase
import com.odys.mototriptracker.domain.usecase.GetTripRouteUseCase
import com.odys.mototriptracker.domain.usecase.ObserveCloudBackendUseCase
import com.odys.mototriptracker.domain.usecase.SaveCloudBackendSettingsUseCase
import com.odys.mototriptracker.domain.usecase.ToggleFavoriteUseCase
import com.odys.mototriptracker.domain.usecase.UpdateTripTitleUseCase
import com.odys.mototriptracker.domain.usecase.UploadTripToCloudUseCase
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
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val uploadTripToCloudUseCase: UploadTripToCloudUseCase,
    private val observeCloudBackend: ObserveCloudBackendUseCase,
    private val saveCloudBackendSettings: SaveCloudBackendSettingsUseCase,
    private val exportGpxUseCase: ExportGpxUseCase,
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
            val backend = observeCloudBackend()
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
                    isLoading = false,
                    backendUrl = backend.baseUrl,
                    displayName = backend.displayName,
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
        val backend = observeCloudBackend()
        _uiState.value = RideSummaryUiState(
            trip = details.trip,
            routePoints = details.routePoints,
            moments = moments,
            isLoading = false,
            backendUrl = backend.baseUrl,
            displayName = backend.displayName,
            uploadStatus = _uiState.value.uploadStatus,
        )
    }

    fun saveBackendSettings(url: String, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backend = saveCloudBackendSettings(url, displayName)
            _uiState.value = _uiState.value.copy(
                backendUrl = backend.baseUrl,
                displayName = backend.displayName,
                uploadStatus = CloudUploadStatus.Idle,
            )
        }
    }

    fun uploadToCloud() {
        if (_uiState.value.uploadStatus is CloudUploadStatus.Uploading) return
        val backend = observeCloudBackend()
        if (!backend.isEnabled) {
            _uiState.value = _uiState.value.copy(
                uploadStatus = CloudUploadStatus.Error("Set a server URL first"),
            )
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(uploadStatus = CloudUploadStatus.Uploading)
            val result = uploadTripToCloudUseCase.uploadNow(tripId)
            _uiState.value = _uiState.value.copy(
                uploadStatus = result.fold(
                    onSuccess = {
                        AppLogger.i(AppLogger.Category.APP, "Manual cloud upload ok trip id=$tripId")
                        CloudUploadStatus.Success
                    },
                    onFailure = {
                        AppLogger.e(AppLogger.Category.APP, "Manual cloud upload failed", it)
                        CloudUploadStatus.Error(it.message ?: "Upload failed")
                    },
                ),
                displayName = observeCloudBackend().displayName,
            )
        }
    }

    fun deleteTrip() {
        viewModelScope.launch(Dispatchers.IO) {
            deleteTripUseCase(tripId)
            AppLogger.i(AppLogger.Category.UI, "Delete requested for trip id=$tripId")
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }

    /** Builds GPX XML for the loaded trip, or null if the trip is missing. */
    fun exportGpx(): String? {
        val state = _uiState.value
        val trip = state.trip ?: return null
        return exportGpxUseCase(trip, state.routePoints)
    }
}
