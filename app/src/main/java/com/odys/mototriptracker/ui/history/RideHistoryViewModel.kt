package com.odys.mototriptracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.data.export.displayTitle
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.domain.usecase.GetTripHistoryUseCase
import com.odys.mototriptracker.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RideHistoryViewModel @Inject constructor(
    private val getTripHistoryUseCase: GetTripHistoryUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
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
                    visibleRides = filterRides(
                        history,
                        state.selectedTab,
                        state.searchQuery,
                        state.filters
                    )
                )
            }
        }
    }

    fun selectTab(tab: RideHistoryTab) {
        _uiState.update { state ->
            state.copy(
                selectedTab = tab,
                visibleRides = filterRides(state.allRides, tab, state.searchQuery, state.filters)
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                visibleRides = filterRides(state.allRides, state.selectedTab, query, state.filters)
            )
        }
    }

    fun updateFilters(filters: RideHistoryFilters) {
        _uiState.update { state ->
            state.copy(
                filters = filters,
                visibleRides = filterRides(
                    state.allRides,
                    state.selectedTab,
                    state.searchQuery,
                    filters
                )
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
                    visibleRides = filterRides(
                        history,
                        state.selectedTab,
                        state.searchQuery,
                        state.filters
                    )
                )
            }
        }
    }

    private fun filterRides(
        rides: List<TripEntity>,
        tab: RideHistoryTab,
        query: String,
        filters: RideHistoryFilters
    ): List<TripEntity> {
        val scoped = when (tab) {
            RideHistoryTab.ALL -> rides
            RideHistoryTab.FAVORITES -> rides.filter { it.isFavorite }
        }

        val dateScoped = scoped.filter { ride -> matchesDateFilter(ride, filters) }

        val normalized = query.trim().lowercase(Locale.getDefault())
        if (normalized.isEmpty()) return dateScoped

        return dateScoped.filter { ride -> matchesQuery(ride, normalized) }
    }

    private fun matchesDateFilter(ride: TripEntity, filters: RideHistoryFilters): Boolean {
        val range = dateRangeFor(filters) ?: return true
        val time = ride.startTime
        return time in range.first..range.second
    }

    private fun matchesQuery(ride: TripEntity, query: String): Boolean {
        val title = ride.displayTitle().lowercase(Locale.getDefault())
        val start = formatSearchDate(ride.startTime)
        val end = formatSearchDate(ride.endTime)
        val distance = String.format(Locale.getDefault(), "%.1f", ride.distanceMeters / 1000f)
        val avg = ride.avgSpeed.toInt().toString()
        val max = ride.maxSpeed.toInt().toString()

        return title.contains(query) ||
            start.contains(query) ||
            end.contains(query) ||
            distance.contains(query) ||
            avg.contains(query) ||
            max.contains(query) ||
            (query.contains("favor") && ride.isFavorite)
    }

    private fun formatSearchDate(timeMs: Long): String {
        if (timeMs <= 0L) return ""
        val formats = listOf(
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "MMM dd",
            "MMMM",
            "yyyy",
            "EEE"
        )
        return formats.joinToString(" ") { pattern ->
            SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timeMs)).lowercase(Locale.getDefault())
        }
    }

    companion object {
        /** Returns inclusive [startMs, endMs], or null when no date filter. */
        fun dateRangeFor(filters: RideHistoryFilters): Pair<Long, Long>? {
            val now = Calendar.getInstance()
            return when (filters.datePreset) {
                DateFilterPreset.ANY -> null
                DateFilterPreset.TODAY -> dayRange(now)
                DateFilterPreset.YESTERDAY -> {
                    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                    dayRange(yesterday)
                }
                DateFilterPreset.THIS_WEEK -> {
                    val start = Calendar.getInstance().apply {
                        firstDayOfWeek = Calendar.MONDAY
                        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                        setToStartOfDay()
                    }
                    val end = Calendar.getInstance().apply { setToEndOfDay() }
                    start.timeInMillis to end.timeInMillis
                }
                DateFilterPreset.THIS_MONTH -> {
                    val start = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        setToStartOfDay()
                    }
                    val end = Calendar.getInstance().apply { setToEndOfDay() }
                    start.timeInMillis to end.timeInMillis
                }
                DateFilterPreset.CUSTOM -> {
                    val from = filters.customFromMs ?: return null
                    val to = filters.customToMs ?: from
                    val start = minOf(from, to)
                    val end = maxOf(from, to)
                    start to end
                }
            }
        }

        private fun dayRange(day: Calendar): Pair<Long, Long> {
            val start = (day.clone() as Calendar).apply { setToStartOfDay() }.timeInMillis
            val end = (day.clone() as Calendar).apply { setToEndOfDay() }.timeInMillis
            return start to end
        }

        private fun Calendar.setToStartOfDay() {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        private fun Calendar.setToEndOfDay() {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
    }
}
