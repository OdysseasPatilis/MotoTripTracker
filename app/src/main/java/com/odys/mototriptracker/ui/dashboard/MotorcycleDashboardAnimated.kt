package com.odys.mototriptracker.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.domain.TripStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MotorcycleDashboardAnimated(
    stats: TripStats,
    isTracking: Boolean,
    isLocationEnabled: Boolean,
    onStartRide: () -> Unit,
    onStopRide: () -> Unit,
    onViewHistory: () -> Unit // New callback for navigation
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = stats.speed, label = "SpeedAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- TOP BAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onViewHistory) {
                Text("HISTORY", color = Color.Cyan, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f)) // Pushes content to center

        SpeedGauge(speed = animatedSpeed, maxSpeed = stats.maxSpeed)
        GForceMeter(
            currentG = stats.currentGForce,
            maxG = stats.maxGForce,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        RideStats(stats)

        Spacer(modifier = Modifier.height(10.dp))

        MovingStoppedBar(stats)

        // --- NEW TIME STATS ---
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Moving: ${formatSecondsToTime(stats.movingTime)}", color = Color.Green, fontSize = 14.sp)
            Text("Stopped: ${formatSecondsToTime(stats.stoppedTime)}", color = Color.Red, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Started: ${formatTimestampToDate(stats.tripStartTime)}",
            color = Color.Gray,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.weight(1f)) // Pushes button to bottom

        Button(
            onClick = {
                if (isTracking) onStopRide() else onStartRide()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            enabled = isLocationEnabled || isTracking,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) Color.Red else Color.DarkGray,
                disabledContainerColor = Color.DarkGray.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = when {
                    isTracking -> "STOP RIDE"
                    !isLocationEnabled -> "ENABLE GPS TO START"
                    else -> "START RIDE"
                },
                fontSize = 20.sp,
                color = if (isLocationEnabled || isTracking) Color.White else Color.Gray
            )
        }
    }
}

fun formatSecondsToTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun formatTimestampToDate(timeMillis: Long): String {
    if (timeMillis == 0L) return "--"
    val formatter = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
    return formatter.format(Date(timeMillis))
}