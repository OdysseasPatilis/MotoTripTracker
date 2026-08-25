package com.odys.mototriptracker.ui.route

import androidx.compose.foundation.background
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
import com.odys.mototriptracker.ui.theme.LocalAppPalette

@Composable
fun FullRouteRoute(
    onBack: () -> Unit,
    viewModel: FullRouteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalAppPalette.current

    LaunchedEffect(uiState.notFound) {
        if (uiState.notFound) onBack()
    }

    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.bgDeep),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = palette.neonGreen)
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.bgDeep),
                contentAlignment = Alignment.Center
            ) {
                Text("Route not found", color = palette.textMuted)
            }
        }
    }
}
