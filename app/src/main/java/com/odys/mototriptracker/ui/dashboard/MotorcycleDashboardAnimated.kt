package com.odys.mototriptracker.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.domain.TripStats

@Composable
fun MotorcycleDashboardAnimated(
    stats: TripStats,
    onStartRide: () -> Unit,
    onStopRide: () -> Unit
) {

    val animatedSpeed = animateFloatAsState(stats.speed).value
    val isRiding = stats.speed > 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        SpeedGauge(
            speed = animatedSpeed,
            maxSpeed = stats.maxSpeed
        )

        Spacer(modifier = Modifier.height(30.dp))

        RideStats(stats)

        Spacer(modifier = Modifier.height(30.dp))

        MovingStoppedBar(stats)

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                if (isRiding) onStopRide()
                else onStartRide()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(
                text = if (isRiding) "STOP RIDE" else "START RIDE",
                fontSize = 20.sp
            )
        }
    }
}