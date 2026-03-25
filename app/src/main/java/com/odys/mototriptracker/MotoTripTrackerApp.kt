package com.odys.mototriptracker

import android.app.Application
import com.odys.mototriptracker.data.MyObjectBox
import com.odys.mototriptracker.data.trip.TripRepository
import com.odys.mototriptracker.domain.GForceTracker
import com.odys.mototriptracker.domain.SpeedFilter
import com.odys.mototriptracker.domain.StopDetector
import com.odys.mototriptracker.domain.TripManager
import io.objectbox.BoxStore

class MotoTripTrackerApp : Application() {

    lateinit var boxStore: BoxStore
        private set

    lateinit var tripManager: TripManager
        private set

    lateinit var tripRepository: TripRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize ObjectBox
        boxStore = MyObjectBox.builder().androidContext(this).build()

        // 2. Initialize the shared managers
        val speedFilter = SpeedFilter()
        val stopDetector = StopDetector()
        val gForceTracker = GForceTracker(this)


        // Now boxStore actually exists when we pass it in!
        tripRepository = TripRepository(this,boxStore)
        tripManager = TripManager(speedFilter, stopDetector,tripRepository,gForceTracker)

    }
}