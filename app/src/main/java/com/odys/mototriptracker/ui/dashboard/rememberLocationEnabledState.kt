package com.odys.mototriptracker.ui.dashboard

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
@Composable
fun rememberLocationEnabledState(context: Context): State<Boolean> {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // Check initial state
    val isEnabled = remember {
        mutableStateOf(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    isEnabled.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                }
            }
        }
        // Listen for the system broadcasting that location settings changed
        context.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return isEnabled
}