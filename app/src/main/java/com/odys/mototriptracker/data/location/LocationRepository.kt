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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository(context: Context) {

    private val fusedLocation = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // Handled in the Service layer
    fun getLocationFlow(): Flow<Location> = callbackFlow {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L // Interval: 2 seconds
        )
            // Optional but highly recommended: Minimum wait time between updates
            .setMinUpdateIntervalMillis(1000L)
            // 🏍️ THE MAGIC BULLET: Only give me an update if they moved at least 2 meters.
            // Prevents battery drain and duplicate points at stoplights!
            .setMinUpdateDistanceMeters(2f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    // trySend is safe here because callbackFlow provides a buffer automatically
                    trySend(location)
                    println("LocationRepo New location: ${location.latitude}, ${location.longitude}, speed: ${location.speed}")
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
            println("LocationRepo: Flow closed. Removing location updates.")
            fusedLocation.removeLocationUpdates(locationCallback)
        }
    }
}