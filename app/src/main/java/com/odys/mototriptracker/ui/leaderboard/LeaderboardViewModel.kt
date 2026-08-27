package com.odys.mototriptracker.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.domain.usecase.GetLeaderboardUseCase
import com.odys.mototriptracker.domain.usecase.LeaderboardCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val getLeaderboardUseCase: GetLeaderboardUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val category = _uiState.value.selectedCategory
            val entries = getLeaderboardUseCase(category)
            _uiState.update {
                it.copy(entries = entries, isLoading = false)
            }
        }
    }

    fun selectCategory(category: LeaderboardCategory) {
        if (category == _uiState.value.selectedCategory) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(selectedCategory = category, isLoading = true) }
            val entries = getLeaderboardUseCase(category)
            _uiState.update {
                it.copy(entries = entries, isLoading = false)
            }
        }
    }
}
