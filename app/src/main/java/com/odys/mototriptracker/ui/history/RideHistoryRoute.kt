package com.odys.mototriptracker.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odys.mototriptracker.ui.dashboard.RideHistoryScreen

@Composable
fun RideHistoryRoute(
    onBack: () -> Unit,
    onRideClick: (Long) -> Unit,
    viewModel: RideHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    RideHistoryScreen(
        rides = uiState.visibleRides,
        selectedTab = uiState.selectedTab,
        searchQuery = uiState.searchQuery,
        filters = uiState.filters,
        onBack = onBack,
        onRideClick = { ride -> onRideClick(ride.id) },
        onToggleFavorite = viewModel::toggleFavorite,
        onSelectTab = viewModel::selectTab,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onFiltersChange = viewModel::updateFilters,
        onClearFilters = viewModel::clearFilters
    )
}
