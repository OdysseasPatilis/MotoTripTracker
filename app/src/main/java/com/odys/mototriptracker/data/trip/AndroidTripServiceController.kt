package com.odys.mototriptracker.data.trip

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.odys.mototriptracker.service.TripForegroundService

class AndroidTripServiceController(
    private val context: Context // Use Application Context here
) : TripServiceController {

    override fun startService() {
        val intent = Intent(context, TripForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopService() {
        val intent = Intent(context, TripForegroundService::class.java)
        context.stopService(intent)
    }
}