package com.odys.mototriptracker.data.navigation

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MotoTravelEstimatorStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var filterBenefit: Double
        get() = if (!prefs.contains(KEY_FILTER_BENEFIT)) {
            MotoTravelEstimator.DEFAULT_FILTER_BENEFIT
        } else {
            prefs.getFloat(KEY_FILTER_BENEFIT, MotoTravelEstimator.DEFAULT_FILTER_BENEFIT.toFloat())
                .toDouble()
                .coerceIn(0.15, 0.75)
        }
        set(value) {
            prefs.edit {
                putFloat(KEY_FILTER_BENEFIT, value.coerceIn(0.15, 0.75).toFloat())
            }
        }

    fun estimate(distanceMeters: Double, carTravelTimeSeconds: Double): MotoTravelEstimator.Estimate =
        MotoTravelEstimator.estimate(distanceMeters, carTravelTimeSeconds, filterBenefit)

    fun learn(from: NavTimingResult) {
        filterBenefit = MotoTravelEstimator.learnedBenefit(from, filterBenefit)
    }

    companion object {
        private const val PREFS_NAME = "moto_app_prefs"
        private const val KEY_FILTER_BENEFIT = "moto_nav_filter_benefit"
    }
}
