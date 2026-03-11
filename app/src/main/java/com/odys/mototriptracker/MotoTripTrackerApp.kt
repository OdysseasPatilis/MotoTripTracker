package com.odys.mototriptracker

import android.app.Application
import com.odys.mototriptracker.data.trip.MyObjectBox
import io.objectbox.BoxStore

class MotoTripTrackerApp : Application() {

    lateinit var boxStore: BoxStore
        private set

    override fun onCreate() {
        super.onCreate()

        boxStore = MyObjectBox.builder()
            .androidContext(this)
            .build()
    }
}