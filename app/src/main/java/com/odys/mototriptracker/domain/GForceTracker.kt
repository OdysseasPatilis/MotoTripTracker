package com.odys.mototriptracker.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class GForceTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    // Store the data for the GPS loop to read
    var currentGForce: Float = 0f
        private set

    var maxSessionGForce: Float = 0f
        private set

    fun startTracking() {
        // Reset stats for the new ride
        currentGForce = 0f
        maxSessionGForce = 0f

        // Register the listener at a standard UI rate (~15-20ms per tick)
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // 1. Calculate the true 3D magnitude
            val accelerationMps2 = sqrt((x * x) + (y * y) + (z * z))
            val rawG = accelerationMps2 / 9.81f

            // 2. Filter out tiny micro-vibrations (engine rumble, etc.)
            // Only register Gs above 0.05 to avoid UI noise when sitting still
            val meaningfulG = if (rawG > 0.05f) rawG else 0f

            // 3. Smooth the current G for the UI (Low-Pass Filter)
            // This prevents the number on the screen from flickering wildly
            currentGForce = (currentGForce * 0.8f) + (meaningfulG * 0.2f)

            // 4. Catch the absolute peak instantly
            if (meaningfulG > maxSessionGForce) {
                maxSessionGForce = meaningfulG
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for linear acceleration
    }
}