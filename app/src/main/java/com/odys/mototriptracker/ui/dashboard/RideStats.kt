package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.domain.TripStats

@Composable
fun RideStats(stats: TripStats) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {

        StatItem(
            title = "Distance",
            value = String.format("%.1f km", stats.distanceKm)
        )

        StatItem(
            title = "Max Speed",
            value = "${stats.maxSpeed.toInt()} km/h"
        )
    }
}

@Composable
fun StatItem(
    title: String,
    value: String
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = title,
            color = Color.Gray,
            fontSize = 14.sp
        )

        Text(
            text = value,
            color = Color.White,
            fontSize = 20.sp
        )
    }
}