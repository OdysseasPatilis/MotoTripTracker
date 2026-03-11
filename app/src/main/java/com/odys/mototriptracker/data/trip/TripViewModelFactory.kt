package com.odys.mototriptracker.data.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.odys.mototriptracker.domain.SpeedFilter
import com.odys.mototriptracker.domain.StopDetector
import com.odys.mototriptracker.domain.TripManager
import io.objectbox.BoxStore

class TripViewModelFactory(
    private val boxStore: BoxStore
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(TripViewModel::class.java)) {

            val speedFilter = SpeedFilter()
            val stopDetector = StopDetector()

            val tripManager = TripManager(
                speedFilter,
                stopDetector
            )

            val tripRepository = TripRepository(boxStore)

            return TripViewModel(
                tripManager,
                tripRepository
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}