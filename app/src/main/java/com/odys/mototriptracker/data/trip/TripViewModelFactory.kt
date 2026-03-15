package com.odys.mototriptracker.data.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.odys.mototriptracker.domain.SpeedFilter
import com.odys.mototriptracker.domain.StopDetector
import com.odys.mototriptracker.domain.TripManager
import io.objectbox.BoxStore

class TripViewModelFactory(
    private val tripManager: TripManager,
    private val tripRepository: TripRepository,
    private val serviceController: TripServiceController
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TripViewModel::class.java)) {
            // Pass the shared instances directly to the ViewModel
            return TripViewModel(
                tripManager,
                tripRepository,
                serviceController
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}