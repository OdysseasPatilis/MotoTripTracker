package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedGauge(
    speed: Float,
    maxSpeed: Float
) {

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {

        Canvas(modifier = Modifier.fillMaxSize()) {

            val startAngle = 135f
            val totalSweep = 270f
            val strokeWidth = 22f

            drawArc(
                color = Color.DarkGray,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )

            val speedSweep = (speed / 150f) * totalSweep

            drawArc(
                color = Color.Cyan,
                startAngle = startAngle,
                sweepAngle = speedSweep,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )

            val maxSweep = (maxSpeed / 150f) * totalSweep
            val angle = Math.toRadians((startAngle + maxSweep).toDouble())

            val radius = size.minDimension / 2 - strokeWidth
            val cx = size.width / 2
            val cy = size.height / 2

            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()

            drawCircle(
                color = Color.Yellow,
                radius = 8f,
                center = Offset(x, y)
            )
        }

        Text(
            text = "${speed.toInt()} km/h",
            color = Color.White,
            fontSize = 40.sp
        )
    }
}