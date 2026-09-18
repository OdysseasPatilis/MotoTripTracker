package com.odys.mototriptracker.data.location

import android.location.Location
import com.odys.mototriptracker.domain.GpsSample

fun Location.toGpsSample(): GpsSample = GpsSample(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speedMps = if (hasSpeed()) speed else 0f,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    bearingDeg = if (hasBearing()) bearing else null,
    timeMs = time,
    hasAltitude = hasAltitude(),
    hasSpeed = hasSpeed(),
)
