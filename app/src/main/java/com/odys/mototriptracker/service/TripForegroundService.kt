package com.odys.mototriptracker.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private var watchdogJob: Job? = null
    private var isForegroundStarted = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastFixAtMs: Long = 0L

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
        acquireWakeLock()
        ensureForeground(contentText = "Tracking your ride")
        startLocationCollection()
        startGpsWatchdog()
        AppLogger.i(AppLogger.Category.SERVICE, "Tracking started")
    }

    private fun pauseTracking() {
        // Keep GPS running so the dashboard signal indicator stays live (iOS parity).
        ensureForeground(contentText = "Ride paused")
        AppLogger.i(AppLogger.Category.SERVICE, "Tracking paused (GPS still active)")
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun resumeTracking() {
        acquireWakeLock()
        ensureForeground(contentText = "Tracking your ride")
        startLocationCollection()
        startGpsWatchdog()
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
                    lastFixAtMs = System.currentTimeMillis()
                    tripManager.onLocationUpdate(location)
                    speedLimitResolver.onLocationUpdate(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speedMps = if (location.hasSpeed()) location.speed else -1f,
                        scope = lifecycleScope
                    )
                }
            } catch (t: Throwable) {
                AppLogger.e(AppLogger.Category.SERVICE, "Location collection failed", t)
            }
        }
    }

    private fun startGpsWatchdog() {
        if (watchdogJob?.isActive == true) return
        lastFixAtMs = System.currentTimeMillis()
        watchdogJob = lifecycleScope.launch {
            while (isActive) {
                delay(GPS_WATCHDOG_INTERVAL_MS)
                if (!tripManager.sessionState.value.isActive || tripManager.sessionState.value.isPaused) {
                    continue
                }
                val silence = System.currentTimeMillis() - lastFixAtMs
                if (silence >= GPS_SILENCE_WARN_MS) {
                    AppLogger.w(
                        AppLogger.Category.SERVICE,
                        "No GPS fix for ${silence / 1000}s while tracking (screen may be off / OEM throttle)"
                    )
                }
            }
        }
    }

    private fun stopLocationCollection() {
        if (locationJob != null) {
            AppLogger.d(AppLogger.Category.SERVICE, "Location collection stopped")
        }
        locationJob?.cancel()
        locationJob = null
        watchdogJob?.cancel()
        watchdogJob = null
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ride Tracking")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ride Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps GPS active while recording a ride"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MotoTripTracker:RideGps"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        AppLogger.i(AppLogger.Category.SERVICE, "PARTIAL_WAKE_LOCK acquired")
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                AppLogger.i(AppLogger.Category.SERVICE, "PARTIAL_WAKE_LOCK released")
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        AppLogger.i(AppLogger.Category.SERVICE, "Service onDestroy")
        stopLocationCollection()
        releaseWakeLock()
        isForegroundStarted = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep recording while a ride is active — swiping the task away must not kill GPS.
        if (tripManager.sessionState.value.isActive) {
            AppLogger.w(
                AppLogger.Category.SERVICE,
                "Task removed — keeping location FGS alive (ride active)"
            )
            acquireWakeLock()
            ensureForeground(
                contentText = if (tripManager.sessionState.value.isPaused) {
                    "Ride paused"
                } else {
                    "Tracking your ride"
                }
            )
            return
        }
        AppLogger.w(AppLogger.Category.SERVICE, "Task removed — stopping service")
        stopLocationCollection()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.odys.mototriptracker.action.START"
        const val ACTION_PAUSE = "com.odys.mototriptracker.action.PAUSE"
        const val ACTION_RESUME = "com.odys.mototriptracker.action.RESUME"

        private const val CHANNEL_ID = "ride_channel"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L // 12h safety cap
        private const val GPS_WATCHDOG_INTERVAL_MS = 15_000L
        private const val GPS_SILENCE_WARN_MS = 20_000L
    }
}
