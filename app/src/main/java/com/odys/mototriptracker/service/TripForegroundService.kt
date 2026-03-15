package com.odys.mototriptracker.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.odys.mototriptracker.MotoTripTrackerApp
import com.odys.mototriptracker.R
import com.odys.mototriptracker.data.location.LocationRepository
import com.odys.mototriptracker.domain.TripManager
import kotlinx.coroutines.launch

class TripForegroundService : LifecycleService() {

    private lateinit var locationRepository: LocationRepository
    private lateinit var tripManager: TripManager

    // Keep track of whether we've already started to avoid double-starting flows
    private var isTracking = false

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize our dependencies ONCE
        locationRepository = LocationRepository(this)

        // Grab the SINGLETON TripManager from the Application class!
        val app = applicationContext as MotoTripTrackerApp
        tripManager = app.tripManager

        createNotificationChannel()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId) // Required for LifecycleService

        // Prevent starting the coroutine and location updates multiple times
        if (!isTracking) {
            isTracking = true

            // 2. Start the foreground notification here
            ServiceCompat.startForeground(
                this,
                1,
                createNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )

            // 3. Start collecting locations
            lifecycleScope.launch {
                // Just call the flow directly! It starts the GPS automatically.
                locationRepository.getLocationFlow().collect { location ->
                    tripManager.onLocationUpdate(location)
                }
            }
        }

        // START_STICKY tells Android: "If you kill this service for memory,
        // restart it as soon as you can."
        return START_STICKY
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "ride_channel")
            .setContentTitle("Ride Tracking")
            .setContentText("Tracking your ride")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure this icon exists!
            .setOngoing(true) // Prevents the user from swiping it away
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ride_channel",
                "Ride Tracking",
                NotificationManager.IMPORTANCE_LOW // Changed to LOW for quiet background operation
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isTracking = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        isTracking = false


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }
}