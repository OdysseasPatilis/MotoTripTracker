package com.odys.mototriptracker.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.HandlerThread
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
        AppLogger.i(AppLogger.Category.LOCATION, "requestLocationUpdates interval=1000ms (screen-off hardened)")

        // Dedicated looper so location delivery isn't starved when the main thread sleeps.
        val thread = HandlerThread("moto-gps").also { it.start() }
        val looper = thread.looper
        val handler = Handler(looper)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    if (!location.hasAccuracy() || location.accuracy < 0f) {
                        AppLogger.d(AppLogger.Category.LOCATION, "Ignored point without accuracy")
                        continue
                    }
                    // Emit weak fixes too so GpsQuality / signal bars can show POOR/FAIR.
                    // TripManager.SpeedFilter still gates stats recording.
                    if (location.accuracy > 20f && LogThrottle.shouldLog("location.weak", 10_000L)) {
                        AppLogger.d(
                            AppLogger.Category.LOCATION,
                            "Weak GPS accuracy=${location.accuracy}m (UI only until accepted by filter)"
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
            looper
        )

        awaitClose {
            AppLogger.i(AppLogger.Category.LOCATION, "Flow closed — removing location updates")
            fusedLocation.removeLocationUpdates(locationCallback)
            handler.post { thread.quitSafely() }
        }
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val MIN_UPDATE_INTERVAL_MS = 500L
        /** Cap batching so Doze can't hold fixes for tens of seconds. */
        private const val MAX_UPDATE_DELAY_MS = 2_000L
    }
}
