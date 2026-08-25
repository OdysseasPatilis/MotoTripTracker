package com.odys.mototriptracker.ui.summary

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
import com.odys.mototriptracker.ui.dashboard.RideSummaryScreenUpdate
import com.odys.mototriptracker.ui.theme.LocalAppPalette

@Composable
fun RideSummaryRoute(
    onBack: () -> Unit,
    onViewRoute: (Long) -> Unit,
    viewModel: RideSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalAppPalette.current

    LaunchedEffect(uiState.isDeleted, uiState.notFound) {
        if (uiState.isDeleted || uiState.notFound) {
            onBack()
        }
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
            val trip = uiState.trip!!
            RideSummaryScreenUpdate(
                summary = trip,
                onBack = onBack,
                onDelete = viewModel::deleteTrip,
                onViewRoute = { onViewRoute(trip.id) }
            )
        }

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.bgDeep),
                contentAlignment = Alignment.Center
            ) {
                Text("Trip not found", color = palette.textMuted)
            }
        }
    }
}
