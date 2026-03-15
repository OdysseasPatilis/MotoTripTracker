package com.odys.mototriptracker.data.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odys.mototriptracker.domain.TripManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TripViewModel(
    private val tripManager: TripManager,
    private val tripRepository: TripRepository,
    private val serviceController: TripServiceController
) : ViewModel() {

    val tripStats = tripManager.tripStats

    // Explicit tracking state for your UI
    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()
    private val _tripHistory = MutableStateFlow<List<TripEntity>>(emptyList())
    val tripHistory = _tripHistory.asStateFlow()

    fun startRide() {
        if (_isTracking.value) return // Prevent accidental double-starts

        println("startRide")
        _isTracking.value = true
        tripManager.startTrip()

        // Delegate to the controller
        serviceController.startService()
    }

    fun stopRide() {
        if (!_isTracking.value) return

        println("stopRide")

        // 1. Instantly update the UI
        _isTracking.value = false

        // 2. Stop the tracking logic
        tripManager.stopTrip()

        // 3. Stop the Android Foreground Service
        serviceController.stopService()

        // 4. Grab the final snapshot of the stats
        val trip = tripManager.tripStats.value

        // 5. Fire off the database save on a background thread!
        viewModelScope.launch(Dispatchers.IO) {

            // Optional but highly recommended: Don't save accidental taps
            if (trip.distanceMeters > 50f) {
                tripRepository.saveTrip(trip)
                println("Ride saved to ObjectBox successfully!")
            } else {
                println("Ride was less than 50 meters. Discarding junk data.")
            }

        }
    }
    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            // Fetch all saved trips from the repository
            val history = tripRepository.getTrips()

            // Reverse it so the newest trips are at the top of the list
            _tripHistory.value = history.reversed()
        }
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Delete from the database
            tripRepository.deleteTrip(tripId)

            // 2. Refresh the list so the Composable instantly removes the card
            loadHistory()
        }
    }
}