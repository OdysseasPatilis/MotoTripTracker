package com.odys.mototriptracker

import android.app.Application
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MotoTripTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.i(AppLogger.Category.APP, "Application started")
    }
}
