package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.domain.TripStats

@Composable
fun RideStats(stats: TripStats) {
    // Calculate total time from our two existing counters
    val totalTripTime = stats.tripTime

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // --- Row 1: Primary Trip Data ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                title = "Distance",
                value = String.format("%.1f km", stats.distanceMeters / 1000f)
            )
            StatItem(
                title = "Total Time",
                value = formatSecondsToTime(totalTripTime)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Row 2: Moving vs Stopped ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                title = "Moving",
                value = formatSecondsToTime(stats.movingTime)
            )
            StatItem(
                title = "Stopped",
                value = formatSecondsToTime(stats.stoppedTime)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Row 3: Performance ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                title = "Avg Speed",
                value = "${stats.avgSpeed.toInt()} km/h"
            )
            StatItem(
                title = "Elevation",
                value = "+${stats.totalElevationGain.toInt()} m"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Row 4: G-Force ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                title = "Live G",
                value = String.format("%.2f G", stats.currentGForce)
            )
            StatItem(
                title = "Max G",
                value = String.format("%.2f G", stats.maxGForce)
            )
        }
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