package com.odys.mototriptracker.data.camera

import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Reverse-geocodes the rider’s ISO country with a distance/time cache. */
@Singleton
class TrafficCameraCountryResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastResolvedAtMs: Long? = null
    private var lastCountryCode: String? = null

    /** Returns uppercase ISO 3166-1 alpha-2, or null if unknown. */
    suspend fun resolve(location: Location): String? {
        val now = System.currentTimeMillis()
        val cached = lastCountryCode
        if (cached != null &&
            !shouldRefresh(
                lastLat = lastLat,
                lastLng = lastLng,
                lastResolvedAtMs = lastResolvedAtMs,
                newLat = location.latitude,
                newLng = location.longitude,
                nowMs = now,
            )
        ) {
            return cached
        }

        val code = withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                addresses?.firstOrNull()?.countryCode?.uppercase(Locale.US)
            }.onFailure {
                AppLogger.w(
                    AppLogger.Category.TRAFFIC_CAMERA,
                    "Country geocode failed: ${it.message}",
                )
            }.getOrNull()
        }

        lastLat = location.latitude
        lastLng = location.longitude
        lastResolvedAtMs = now
        if (code != null) {
            lastCountryCode = code
        }
        return code ?: lastCountryCode
    }

    companion object {
        const val MIN_DISTANCE_METERS = 5_000f
        const val MIN_INTERVAL_MS = 600_000L

        fun shouldRefresh(
            lastLat: Double?,
            lastLng: Double?,
            lastResolvedAtMs: Long?,
            newLat: Double,
            newLng: Double,
            nowMs: Long,
            minDistanceMeters: Float = MIN_DISTANCE_METERS,
            minIntervalMs: Long = MIN_INTERVAL_MS,
        ): Boolean {
            if (lastLat == null || lastLng == null || lastResolvedAtMs == null) return true
            val moved = TrafficCameraLogic.distanceMeters(lastLat, lastLng, newLat, newLng) >=
                minDistanceMeters
            val waited = nowMs - lastResolvedAtMs >= minIntervalMs
            return moved || waited
        }
    }
}
