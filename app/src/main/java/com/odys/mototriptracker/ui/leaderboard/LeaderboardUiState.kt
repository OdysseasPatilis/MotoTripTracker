package com.odys.mototriptracker.ui.leaderboard

import com.odys.mototriptracker.domain.usecase.LeaderboardCategory
import com.odys.mototriptracker.domain.usecase.LeaderboardEntry

data class LeaderboardUiState(
    val selectedCategory: LeaderboardCategory = LeaderboardCategory.SPEED,
    val entries: List<LeaderboardEntry> = emptyList(),
    val isLoading: Boolean = false
)
