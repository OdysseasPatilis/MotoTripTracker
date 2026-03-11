package com.odys.mototriptracker.data.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class LocationRepository(
    context: Context
) {

    private val fusedLocation =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locations = MutableSharedFlow<Location>()
    val locations: SharedFlow<Location> = _locations

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startLocationUpdates() {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).build()

        fusedLocation.requestLocationUpdates(
            request,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach {
                        _locations.tryEmit(it)
                    }
                }
            },
            Looper.getMainLooper()
        )
    }
}