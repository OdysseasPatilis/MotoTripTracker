package com.odys.mototriptracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.odys.mototriptracker.ui.history.RideHistoryRoute
import com.odys.mototriptracker.ui.route.FullRouteRoute
import com.odys.mototriptracker.ui.summary.RideSummaryRoute
import com.odys.mototriptracker.ui.tracker.RideTrackerRoute

@Composable
fun MotoTripNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.TRACKER
    ) {
        composable(Routes.TRACKER) {
            RideTrackerRoute(
                onViewHistory = {
                    navController.navigate(Routes.HISTORY)
                }
            )
        }

        composable(Routes.HISTORY) {
            RideHistoryRoute(
                onBack = { navController.popBackStack() },
                onRideClick = { tripId ->
                    navController.navigate(Routes.summary(tripId))
                }
            )
        }

        composable(
            route = Routes.SUMMARY,
            arguments = listOf(
                navArgument(Routes.TRIP_ID_ARG) { type = NavType.LongType }
            )
        ) {
            RideSummaryRoute(
                onBack = { navController.popBackStack() },
                onViewRoute = { tripId ->
                    navController.navigate(Routes.fullRoute(tripId))
                }
            )
        }

        composable(
            route = Routes.FULL_ROUTE,
            arguments = listOf(
                navArgument(Routes.TRIP_ID_ARG) { type = NavType.LongType }
            )
        ) {
            FullRouteRoute(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
