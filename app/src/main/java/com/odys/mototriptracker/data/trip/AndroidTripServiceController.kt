package com.odys.mototriptracker.data.trip

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.odys.mototriptracker.service.TripForegroundService
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTripServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TripServiceController {

    override fun startService() {
        AppLogger.i(AppLogger.Category.SERVICE, "startForegroundService ACTION_START")
        val intent = Intent(context, TripForegroundService::class.java).apply {
            action = TripForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopService() {
        AppLogger.i(AppLogger.Category.SERVICE, "stopService")
        val intent = Intent(context, TripForegroundService::class.java)
        context.stopService(intent)
    }

    override fun pauseService() {
        AppLogger.i(AppLogger.Category.SERVICE, "pauseService ACTION_PAUSE")
        val intent = Intent(context, TripForegroundService::class.java).apply {
            action = TripForegroundService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    override fun resumeService() {
        AppLogger.i(AppLogger.Category.SERVICE, "resumeService ACTION_RESUME")
        val intent = Intent(context, TripForegroundService::class.java).apply {
            action = TripForegroundService.ACTION_RESUME
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
