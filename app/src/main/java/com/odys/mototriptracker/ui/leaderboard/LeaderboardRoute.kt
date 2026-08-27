package com.odys.mototriptracker.ui.leaderboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LeaderboardRoute(
    onBack: () -> Unit,
    onRideClick: (Long) -> Unit,
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LeaderboardScreen(
        selectedCategory = uiState.selectedCategory,
        entries = uiState.entries,
        onBack = onBack,
        onSelectCategory = viewModel::selectCategory,
        onEntryClick = onRideClick
    )
}
