package com.odys.mototriptracker.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.odys.mototriptracker.MotoTripTrackerApp
import com.odys.mototriptracker.data.trip.AndroidTripServiceController
import com.odys.mototriptracker.data.trip.TripViewModel
import com.odys.mototriptracker.data.trip.TripViewModelFactory

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MotoTripTrackerApp

    // 1. Create the Android-specific controller
    val serviceController = remember { AndroidTripServiceController(app) }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            println("Background location granted! Ready to track with screen off.")
        } else {
            println("Background location denied. App will stop tracking if screen turns off.")
        }
    }
    val basePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineLocationGranted || coarseLocationGranted) {
            // Stage 1 passed! Now we ask for Stage 2 (Background Location)
            // Note: On Android 11+, this takes them to the settings screen.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    // Automatically trigger Stage 1 when the screen loads
    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        basePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }
    // 2. Pass the shared instances from the App class into the Factory
    val viewModel: TripViewModel = viewModel(
        factory = TripViewModelFactory(
            tripManager = app.tripManager,       // Shared!
            tripRepository = app.tripRepository, // Shared!
            serviceController = serviceController
        )
    )

    val stats by viewModel.tripStats.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    // Collect the history list from the ViewModel
    val history by viewModel.tripHistory.collectAsStateWithLifecycle()

    // Simple state to toggle between Dashboard and History views
    var showHistory by remember { mutableStateOf(false) }
    val isLocationEnabled by rememberLocationEnabledState(context)

    if (showHistory) {
        TripHistoryScreen(
            history = history,
            onBack = { showHistory = false }, // Go back to dashboard
            onDeleteTrip = { tripId ->
                viewModel.deleteTrip(tripId) // Trigger the deletion
            }
        )
    }else {
        MotorcycleDashboardAnimated(
            stats = stats,
            isTracking = isTracking,
            isLocationEnabled = isLocationEnabled,
            onStartRide = {
                // Quick sanity check before starting the service
                val hasLocation = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasLocation) viewModel.startRide()
                else {
                    // Tell the user they need to grant permissions first!
                    println("Cannot start ride: Location permission missing.")
                }
            },
            onStopRide = viewModel::stopRide,
            onViewHistory = {
                viewModel.loadHistory() // Load the latest data from ObjectBox
                showHistory = true      // Switch the UI
            }
        )
    }
}