package com.odys.mototriptracker.ui.route

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odys.mototriptracker.ui.dashboard.FullRouteScreenGMaps
import com.odys.mototriptracker.ui.dashboard.TextMuted

@Composable
fun FullRouteRoute(
    onBack: () -> Unit,
    viewModel: FullRouteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.notFound) {
        if (uiState.notFound) onBack()
    }

    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.trip != null -> {
            FullRouteScreenGMaps(
                summary = uiState.trip!!,
                ridePoints = uiState.ridePoints,
                waypoints = uiState.waypoints,
                onBack = onBack
            )
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Route not found", color = TextMuted)
            }
        }
    }
}
