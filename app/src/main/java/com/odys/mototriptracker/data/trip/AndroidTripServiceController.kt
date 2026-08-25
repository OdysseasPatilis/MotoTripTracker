package com.odys.mototriptracker.data.trip

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.odys.mototriptracker.service.TripForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTripServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TripServiceController {

    override fun startService() {
        val intent = Intent(context, TripForegroundService::class.java).apply {
            action = TripForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopService() {
        val intent = Intent(context, TripForegroundService::class.java)
        context.stopService(intent)
    }

    override fun pauseService() {
        val intent = Intent(context, TripForegroundService::class.java).apply {
            action = TripForegroundService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    override fun resumeService() {
        val intent = Intent(context, TripForegroundService::class.java).apply {
            action = TripForegroundService.ACTION_RESUME
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
