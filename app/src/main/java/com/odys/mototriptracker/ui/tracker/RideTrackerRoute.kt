package com.odys.mototriptracker.ui.tracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odys.mototriptracker.ui.dashboard.RideTrackerScreen
import com.odys.mototriptracker.util.AppLogger

@Composable
fun RideTrackerRoute(
    onViewHistory: () -> Unit,
    onViewLeaderboard: () -> Unit = {},
    viewModel: RideTrackerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLocationEnabled by rememberLocationEnabledState(context)

    RequestTrackingPermissions()

    RideTrackerScreen(
        stats = uiState.stats,
        isTracking = uiState.isTracking,
        isLocationEnabled = isLocationEnabled,
        onStartRide = {
            val hasLocation = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasLocation) {
                viewModel.startRide()
            } else {
                AppLogger.w(AppLogger.Category.UI, "Cannot start ride — location permission missing")
            }
        },
        onStopRide = viewModel::stopRide,
        onViewHistory = onViewHistory,
        onViewLeaderboard = onViewLeaderboard,
        onPauseRide = viewModel::togglePause,
        isPaused = uiState.isPaused
    )
}

@Composable
private fun RequestTrackingPermissions() {
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    val basePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if ((fineLocationGranted || coarseLocationGranted) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

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
}

@Composable
fun rememberLocationEnabledState(context: Context): State<Boolean> {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val isEnabled = remember {
        mutableStateOf(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    isEnabled.value =
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        onDispose { context.unregisterReceiver(receiver) }
    }

    return isEnabled
}
