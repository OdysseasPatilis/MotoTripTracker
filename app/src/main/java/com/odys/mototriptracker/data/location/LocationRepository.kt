package com.odys.mototriptracker.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.odys.mototriptracker.domain.RideTimer
import com.odys.mototriptracker.util.AppLogger
import com.odys.mototriptracker.util.LogThrottle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    private val fusedLocation = LocationServices.getFusedLocationProviderClient(context)

    val riderTimer = RideTimer()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    /** Latest fix for dashboard GPS UI (idle / paused / tracking). */
    val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()

    @SuppressLint("MissingPermission") // Handled in the Service / UI permission layer
    fun getLocationFlow(): Flow<Location> = callbackFlow {

        riderTimer.start()
        AppLogger.i(AppLogger.Category.LOCATION, "requestLocationUpdates interval=1000ms")
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    if (!location.hasAccuracy() || location.accuracy < 0f) {
                        AppLogger.d(AppLogger.Category.LOCATION, "Ignored point without accuracy")
                        continue
                    }
                    // Emit weak fixes too so GpsQuality / signal bars can show POOR/FAIR.
                    // TripManager.SpeedFilter still rejects accuracy > 15 m for stats.
                    if (location.accuracy > 20f && LogThrottle.shouldLog("location.weak", 10_000L)) {
                        AppLogger.d(
                            AppLogger.Category.LOCATION,
                            "Weak GPS accuracy=${location.accuracy}m (UI only until ≤15m)"
                        )
                    }
                    _lastLocation.value = location
                    trySend(location)
                }
            }
        }

        fusedLocation.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            AppLogger.i(AppLogger.Category.LOCATION, "Flow closed — removing location updates")
            fusedLocation.removeLocationUpdates(locationCallback)
        }
    }
}
