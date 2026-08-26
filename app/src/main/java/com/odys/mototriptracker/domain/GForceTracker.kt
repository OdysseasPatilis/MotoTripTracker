package com.odys.mototriptracker.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class GForceTracker @Inject constructor(
    @ApplicationContext context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    var currentGForce: Float = 0f
        private set

    var maxSessionGForce: Float = 0f
        private set

    /** Horizontal acceleration magnitude (approx lateral / braking plane). */
    var currentLateralGForce: Float = 0f
        private set

    var maxSessionLateralGForce: Float = 0f
        private set

    fun startTracking(resetSession: Boolean = true) {
        if (resetSession) {
            currentGForce = 0f
            maxSessionGForce = 0f
            currentLateralGForce = 0f
            maxSessionLateralGForce = 0f
        }

        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
        currentGForce = 0f
        currentLateralGForce = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val accelerationMps2 = sqrt((x * x) + (y * y) + (z * z))
        val rawG = accelerationMps2 / 9.81f
        val meaningfulG = if (rawG > 0.05f) rawG else 0f
        currentGForce = (currentGForce * 0.8f) + (meaningfulG * 0.2f)
        if (meaningfulG > maxSessionGForce) {
            maxSessionGForce = meaningfulG
        }

        // Horizontal plane relative to typical portrait mount (x/y).
        val lateralMps2 = sqrt((x * x) + (y * y))
        val rawLateralG = lateralMps2 / 9.81f
        val meaningfulLateral = if (rawLateralG > 0.05f) rawLateralG else 0f
        currentLateralGForce = (currentLateralGForce * 0.8f) + (meaningfulLateral * 0.2f)
        if (meaningfulLateral > maxSessionLateralGForce) {
            maxSessionLateralGForce = meaningfulLateral
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
