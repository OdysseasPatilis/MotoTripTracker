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
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    private val fusedLocation = LocationServices.getFusedLocationProviderClient(context)

    val riderTimer = RideTimer()

    @SuppressLint("MissingPermission") // Handled in the Service layer
    fun getLocationFlow(): Flow<Location> = callbackFlow {

        riderTimer.start()
        AppLogger.i(AppLogger.Category.LOCATION, "requestLocationUpdates interval=1000ms")
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // Interval: 1 second
        )
            // Optional but highly recommended: Minimum wait time between updates
            .setMinUpdateIntervalMillis(1000L)
            // 🏍️ THE MAGIC BULLET: Only give me an update if they moved at least 2 meters.
            // Prevents battery drain and duplicate points at stoplights!
            //.setMinUpdateDistanceMeters(2f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    // 1. THE SHIELD: Ignore points without accuracy data
                    if (!location.hasAccuracy()) {
                        AppLogger.d(AppLogger.Category.LOCATION, "Ignored point without accuracy")
                        continue
                    }
                    // 2. THE FILTER: Throw away "bouncy" points
                    // 15 meters is a great threshold for motorcycles.
                    // Anything higher is usually a cold-start bounce or tall building interference.
                    if (location.accuracy > 15f) {
                        if (LogThrottle.shouldLog("location.bouncy", 10_000L)) {
                            AppLogger.d(
                                AppLogger.Category.LOCATION,
                                "Ignored bouncy GPS point accuracy=${location.accuracy}m"
                            )
                        }
                        continue // Skips this point entirely!
                    }

                    // trySend is safe here because callbackFlow provides a buffer automatically
                    trySend(location)
                }
            }
        }

        fusedLocation.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )

        // awaitClose pauses the coroutine here until the flow is no longer being collected.
        // Once the Service stops collecting, it automatically removes the location updates!
        awaitClose {
            AppLogger.i(AppLogger.Category.LOCATION, "Flow closed — removing location updates")
            fusedLocation.removeLocationUpdates(locationCallback)
        }
    }
}