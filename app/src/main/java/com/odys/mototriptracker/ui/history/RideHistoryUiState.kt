package com.odys.mototriptracker.ui.history

import com.odys.mototriptracker.domain.model.Trip

enum class RideHistoryTab {
    ALL,
    FAVORITES
}

enum class DateFilterPreset {
    ANY,
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM
}

data class RideHistoryFilters(
    val datePreset: DateFilterPreset = DateFilterPreset.ANY,
    /** Inclusive start-of-day millis for CUSTOM; ignored for other presets. */
    val customFromMs: Long? = null,
    /** Inclusive end-of-day millis for CUSTOM; ignored for other presets. */
    val customToMs: Long? = null
) {
    val hasActiveFilters: Boolean
        get() = datePreset != DateFilterPreset.ANY
}

data class RideHistoryUiState(
    val allRides: List<Trip> = emptyList(),
    val visibleRides: List<Trip> = emptyList(),
    val selectedTab: RideHistoryTab = RideHistoryTab.ALL,
    val searchQuery: String = "",
    val filters: RideHistoryFilters = RideHistoryFilters(),
    val isLoading: Boolean = false
)
