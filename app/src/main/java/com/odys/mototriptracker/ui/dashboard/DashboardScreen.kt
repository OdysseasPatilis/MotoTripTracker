package com.odys.mototriptracker.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.odys.mototriptracker.MotoTripTrackerApp
import com.odys.mototriptracker.data.trip.TripViewModel
import com.odys.mototriptracker.data.trip.TripViewModelFactory

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MotoTripTrackerApp

    val viewModel: TripViewModel = viewModel(
        factory = TripViewModelFactory(app.boxStore)
    )

    val stats by viewModel.tripStats.collectAsStateWithLifecycle()
    MotorcycleDashboardAnimated(
        stats = stats,
        onStartRide = { viewModel.startRide(context)},
        onStopRide = { viewModel.stopRide() }
    )
}