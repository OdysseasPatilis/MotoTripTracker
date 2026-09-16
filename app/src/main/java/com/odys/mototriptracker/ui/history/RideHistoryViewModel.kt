package com.odys.mototriptracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.domain.usecase.GetTripHistoryUseCase
import com.odys.mototriptracker.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RideHistoryViewModel @Inject constructor(
    private val getTripHistoryUseCase: GetTripHistoryUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RideHistoryUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val history = getTripHistoryUseCase()
            _uiState.update { state ->
                state.copy(
                    allRides = history,
                    isLoading = false,
                    visibleRides = RideHistoryFilterLogic.filterRides(
                        history,
                        state.selectedTab,
                        state.searchQuery,
                        state.filters,
                    ),
                )
            }
        }
    }

    fun selectTab(tab: RideHistoryTab) {
        _uiState.update { state ->
            state.copy(
                selectedTab = tab,
                visibleRides = RideHistoryFilterLogic.filterRides(
                    state.allRides,
                    tab,
                    state.searchQuery,
                    state.filters,
                ),
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                visibleRides = RideHistoryFilterLogic.filterRides(
                    state.allRides,
                    state.selectedTab,
                    query,
                    state.filters,
                ),
            )
        }
    }

    fun updateFilters(filters: RideHistoryFilters) {
        _uiState.update { state ->
            state.copy(
                filters = filters,
                visibleRides = RideHistoryFilterLogic.filterRides(
                    state.allRides,
                    state.selectedTab,
                    state.searchQuery,
                    filters,
                ),
            )
        }
    }

    fun clearFilters() {
        updateFilters(RideHistoryFilters())
    }

    fun toggleFavorite(tripId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            toggleFavoriteUseCase(tripId)
            val history = getTripHistoryUseCase()
            _uiState.update { state ->
                state.copy(
                    allRides = history,
                    visibleRides = RideHistoryFilterLogic.filterRides(
                        history,
                        state.selectedTab,
                        state.searchQuery,
                        state.filters,
                    ),
                )
            }
        }
    }

    companion object {
        /** Kept for callers/tests that used the VM companion. */
        fun dateRangeFor(filters: RideHistoryFilters) =
            RideHistoryFilterLogic.dateRangeFor(filters)
    }
}
