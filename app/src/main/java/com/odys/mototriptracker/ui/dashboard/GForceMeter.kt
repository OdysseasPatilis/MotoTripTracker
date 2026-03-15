package com.odys.mototriptracker.ui.dashboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GForceMeter(
    currentG: Float,
    maxG: Float,
    modifier: Modifier = Modifier
) {
    // Animate the G-Force value for a smooth "liquid" feel
    val animatedG by animateFloatAsState(
        targetValue = currentG,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "GForceAnimation"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${String.format("%.2f", animatedG)} G",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(8.dp)
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2

            // Draw Background Track
            drawRoundRect(
                color = Color.DarkGray,
                size = size,
                cornerRadius = CornerRadius(4.dp.toPx())
            )

            // Calculate width: Limit to 1.0G for the scale, but allow numbers to go higher
            val gScale = 1.0f
            val fillWidth = (animatedG / gScale) * centerX

            // Acceleration (Green) vs Braking (Red)
            val barColor = if (animatedG >= 0) Color.Green else Color.Red

            drawRect(
                color = barColor,
                topLeft = Offset(if (animatedG >= 0) centerX else centerX + fillWidth, 0f),
                size = androidx.compose.ui.geometry.Size(kotlin.math.abs(fillWidth), height)
            )

            // Center Marker
            drawLine(
                color = Color.White,
                start = Offset(centerX, -4f),
                end = Offset(centerX, height + 4f),
                strokeWidth = 2.dp.toPx()
            )
        }

        Text(
            text = "MAX: ${String.format("%.2f", maxG)} G",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}