package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.odys.mototriptracker.domain.TripStats

@Composable
fun MovingStoppedBar(stats: TripStats) {

    val total = (stats.movingTime + stats.stoppedTime).coerceAtLeast(1)

    val movingFraction = stats.movingTime.toFloat() / total
    val stoppedFraction = stats.stoppedTime.toFloat() / total

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {

        val width = size.width
        val height = size.height

        drawRect(
            color = Color.Green,
            size = androidx.compose.ui.geometry.Size(width * movingFraction, height)
        )

        drawRect(
            color = Color.Red,
            topLeft = Offset(width * movingFraction, 0f),
            size = androidx.compose.ui.geometry.Size(width * stoppedFraction, height)
        )
    }
}