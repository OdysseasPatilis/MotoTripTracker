package com.odys.mototriptracker.data.trip

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.odys.mototriptracker.domain.TripManager
import com.odys.mototriptracker.service.TripForegroundService

class TripViewModel(
    private val tripManager: TripManager,
    private val tripRepository: TripRepository
) : ViewModel() {

    val tripStats = tripManager.tripStats

    fun startRide(context: Context) {

        tripManager.startTrip()

        ContextCompat.startForegroundService(
            context,
            Intent(context, TripForegroundService::class.java)
        )
    }

    fun stopRide() {

        tripManager.stopTrip()

        val trip = tripManager.tripStats.value

        tripRepository.saveTrip(trip)
    }
}