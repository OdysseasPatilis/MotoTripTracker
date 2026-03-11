package com.odys.mototriptracker.service

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.odys.mototriptracker.R
import com.odys.mototriptracker.data.location.LocationRepository
import com.odys.mototriptracker.domain.TripManager
import kotlinx.coroutines.launch

class TripForegroundService : LifecycleService() {

    lateinit var locationRepository: LocationRepository
    lateinit var tripManager: TripManager

    override fun onCreate() {
        super.onCreate()

        startForeground(1, createNotification())

        lifecycleScope.launch {
            locationRepository.locations.collect {
                tripManager.onLocationUpdate(it)
            }
        }

        locationRepository.startLocationUpdates()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "ride_channel")
            .setContentTitle("Ride Tracking")
            .setContentText("Tracking your ride")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}