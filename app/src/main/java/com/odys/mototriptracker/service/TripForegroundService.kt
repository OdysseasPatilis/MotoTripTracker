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
import com.odys.mototriptracker.R
import com.odys.mototriptracker.data.location.LocationRepository
import com.odys.mototriptracker.domain.SpeedLimitResolver
import com.odys.mototriptracker.domain.TripManager
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TripForegroundService : LifecycleService() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var tripManager: TripManager

    @Inject
    lateinit var speedLimitResolver: SpeedLimitResolver

    private var locationJob: Job? = null
    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AppLogger.i(AppLogger.Category.SERVICE, "Service onCreate")
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val action = intent?.action ?: ACTION_START
        AppLogger.i(AppLogger.Category.SERVICE, "onStartCommand action=$action")

        when (action) {
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            else -> startTracking()
        }

        return START_STICKY
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startTracking() {
        speedLimitResolver.reset()
        ensureForeground(contentText = "Tracking your ride")
        startLocationCollection()
        AppLogger.i(AppLogger.Category.SERVICE, "Tracking started")
    }

    private fun pauseTracking() {
        stopLocationCollection()
        ensureForeground(contentText = "Ride paused")
        AppLogger.i(AppLogger.Category.SERVICE, "Tracking paused")
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun resumeTracking() {
        ensureForeground(contentText = "Tracking your ride")
        startLocationCollection()
        AppLogger.i(AppLogger.Category.SERVICE, "Tracking resumed")
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationCollection() {
        if (locationJob?.isActive == true) {
            AppLogger.d(AppLogger.Category.SERVICE, "Location collection already active")
            return
        }

        locationJob = lifecycleScope.launch {
            AppLogger.i(AppLogger.Category.SERVICE, "Location collection flow started")
            try {
                locationRepository.getLocationFlow().collect { location ->
                    tripManager.onLocationUpdate(location)
                    speedLimitResolver.onLocationUpdate(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        scope = lifecycleScope
                    )
                }
            } catch (t: Throwable) {
                AppLogger.e(AppLogger.Category.SERVICE, "Location collection failed", t)
            }
        }
    }

    private fun stopLocationCollection() {
        if (locationJob != null) {
            AppLogger.d(AppLogger.Category.SERVICE, "Location collection stopped")
        }
        locationJob?.cancel()
        locationJob = null
    }

    private fun ensureForeground(contentText: String) {
        val notification = createNotification(contentText)
        if (!isForegroundStarted) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
            isForegroundStarted = true
        } else {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ride Tracking")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ride Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        AppLogger.i(AppLogger.Category.SERVICE, "Service onDestroy")
        stopLocationCollection()
        isForegroundStarted = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        AppLogger.w(AppLogger.Category.SERVICE, "Task removed — stopping service")
        super.onTaskRemoved(rootIntent)
        stopLocationCollection()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.odys.mototriptracker.action.START"
        const val ACTION_PAUSE = "com.odys.mototriptracker.action.PAUSE"
        const val ACTION_RESUME = "com.odys.mototriptracker.action.RESUME"

        private const val CHANNEL_ID = "ride_channel"
        private const val NOTIFICATION_ID = 1
    }
}
