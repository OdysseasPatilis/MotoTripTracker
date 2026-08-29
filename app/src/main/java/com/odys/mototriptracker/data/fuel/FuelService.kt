package com.odys.mototriptracker.data.fuel

import android.content.Context
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class FuelService @Inject constructor(
    @param:ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _tankCapacityLiters = MutableStateFlow(readCapacity())
    val tankCapacityLiters: StateFlow<Double> = _tankCapacityLiters.asStateFlow()

    private val _fuelRemainingLiters = MutableStateFlow(readRemaining(_tankCapacityLiters.value))
    val fuelRemainingLiters: StateFlow<Double> = _fuelRemainingLiters.asStateFlow()

    private val _consumptionLPer100Km = MutableStateFlow(readConsumption())
    val consumptionLPer100Km: StateFlow<Double> = _consumptionLPer100Km.asStateFlow()

    private var consumedDistanceKmThisRide = 0.0

    val rangeRemainingKm: Double
        get() {
            val consumption = _consumptionLPer100Km.value
            if (consumption <= 0) return 0.0
            return (_fuelRemainingLiters.value / consumption) * 100.0
        }

    val fuelFraction: Double
        get() {
            val capacity = _tankCapacityLiters.value
            if (capacity <= 0) return 0.0
            return _fuelRemainingLiters.value / capacity
        }

    val isLowFuel: Boolean
        get() = fuelFraction < 0.2 || rangeRemainingKm < 40.0

    val rangeSummary: String
        get() = if (rangeRemainingKm >= 10) {
            String.format("~%.0f km range", rangeRemainingKm)
        } else {
            String.format("~%.0f km left", max(0.0, rangeRemainingKm))
        }

    fun setTankCapacityLiters(value: Double) {
        val clamped = clamp(value, 5.0, 40.0)
        _tankCapacityLiters.value = clamped
        prefs.edit().putFloat(KEY_CAPACITY, clamped.toFloat()).apply()
        if (_fuelRemainingLiters.value > clamped) {
            setFuelRemainingLiters(clamped)
        }
    }

    fun setFuelRemainingLiters(value: Double) {
        val clamped = clamp(value, 0.0, _tankCapacityLiters.value)
        _fuelRemainingLiters.value = clamped
        prefs.edit().putFloat(KEY_REMAINING, clamped.toFloat()).apply()
    }

    fun setConsumptionLPer100Km(value: Double) {
        val clamped = clamp(value, 2.5, 12.0)
        _consumptionLPer100Km.value = clamped
        prefs.edit().putFloat(KEY_CONSUMPTION, clamped.toFloat()).apply()
    }

    fun fillUp() {
        setFuelRemainingLiters(_tankCapacityLiters.value)
        prefs.edit().putLong(KEY_LAST_FILL, System.currentTimeMillis()).apply()
        AppLogger.i(AppLogger.Category.UI, "Fuel filled to ${_tankCapacityLiters.value} L")
    }

    fun resetRideConsumption() {
        consumedDistanceKmThisRide = 0.0
    }

    fun updateConsumedDistance(tripDistanceKm: Double) {
        val delta = max(0.0, tripDistanceKm - consumedDistanceKmThisRide)
        if (delta <= 0) return
        consumedDistanceKmThisRide = tripDistanceKm
        val burned = delta * (_consumptionLPer100Km.value / 100.0)
        setFuelRemainingLiters(_fuelRemainingLiters.value - burned)
    }

    private fun readCapacity(): Double =
        clamp(prefs.getFloat(KEY_CAPACITY, DEFAULT_CAPACITY.toFloat()).toDouble(), 5.0, 40.0)

    private fun readRemaining(capacity: Double): Double =
        clamp(prefs.getFloat(KEY_REMAINING, capacity.toFloat()).toDouble(), 0.0, capacity)

    private fun readConsumption(): Double =
        clamp(prefs.getFloat(KEY_CONSUMPTION, DEFAULT_CONSUMPTION.toFloat()).toDouble(), 2.5, 12.0)

    private fun clamp(value: Double, minValue: Double, maxValue: Double): Double =
        min(maxValue, max(minValue, value))

    companion object {
        private const val PREFS_NAME = "moto_fuel_prefs"
        private const val KEY_CAPACITY = "tank_capacity_liters"
        private const val KEY_REMAINING = "fuel_remaining_liters"
        private const val KEY_CONSUMPTION = "consumption_l_per_100km"
        private const val KEY_LAST_FILL = "last_fill_date"
        private const val DEFAULT_CAPACITY = 16.0
        private const val DEFAULT_CONSUMPTION = 5.5
    }
}
