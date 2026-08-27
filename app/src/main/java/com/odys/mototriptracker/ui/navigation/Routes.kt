package com.odys.mototriptracker.ui.navigation

object Routes {
    const val TRACKER = "tracker"
    const val HISTORY = "history"
    const val LEADERBOARD = "leaderboard"
    const val SUMMARY = "summary/{tripId}"
    const val FULL_ROUTE = "full_route/{tripId}"

    const val TRIP_ID_ARG = "tripId"

    fun summary(tripId: Long): String = "summary/$tripId"
    fun fullRoute(tripId: Long): String = "full_route/$tripId"
}
