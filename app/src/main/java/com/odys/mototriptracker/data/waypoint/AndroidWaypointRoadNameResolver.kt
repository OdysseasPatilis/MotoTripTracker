package com.odys.mototriptracker.data.waypoint

import android.content.Context
import com.odys.mototriptracker.domain.WaypointRoadNameResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidWaypointRoadNameResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : WaypointRoadNameResolver {
    override fun looksLikeCoordinates(label: String): Boolean =
        WaypointReverseGeocoder.looksLikeCoordinates(label)

    override fun resolveRoadName(latitude: Double, longitude: Double): String =
        WaypointReverseGeocoder.resolveRoadName(context, latitude, longitude)
}
