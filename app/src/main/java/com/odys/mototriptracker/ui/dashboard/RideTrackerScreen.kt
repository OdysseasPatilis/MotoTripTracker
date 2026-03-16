package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.domain.TripStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// --- Color palette ---
val BgDeep    = Color(0xFF0A0A0F)
val BgCard    = Color(0xFF111120)
val BgSurface = Color(0xFF4050E5)
val TextPrimary = Color.White
val TextMuted   = Color(0xFF4A4A6A)
val NeonGreen   = Color(0xFF00E5A0)
val NeonBlue    = Color(0xFF00B4FF)
val NeonRed     = Color(0xFFFF4A6A)

val GradientArcColors  = listOf(NeonGreen, NeonBlue)
val GradientButton     = Brush.horizontalGradient(listOf(NeonGreen, NeonBlue))


@Composable
fun RideTrackerScreen(
    stats: TripStats,
    isTracking: Boolean,
    isLocationEnabled: Boolean,
    onStartRide: () -> Unit,
    onStopRide: () -> Unit,
    onViewHistory: () -> Unit // New callback for navigation
) {
    Scaffold(
        containerColor = BgDeep,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDeep)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 12.dp)
            ) {
                Button(
                    onClick = { if (isTracking) onStopRide() else onStartRide() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            if (isTracking)
                                Brush.horizontalGradient(listOf(NeonRed, Color(0xFFFF6B6B)))
                            else
                                GradientButton
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    enabled = isLocationEnabled || isTracking
                ) {
                    Text(
                        text = when {
                            isTracking -> "STOP RIDE"
                            !isLocationEnabled -> "ENABLE GPS TO START"
                            else -> "START RIDE"
                        },
                        color = if (isLocationEnabled || isTracking) BgDeep else Color.Gray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    ){ innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "RIDE TRACKER", color = TextMuted, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 2.sp
                )
                TextButton(onClick = onViewHistory) {
                    Text(
                        "HISTORY", color = NeonGreen, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = 1.sp
                    )
                }
            }
            // Speedometer card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1A1A2E))
                    .padding(24.dp, 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SpeedometerArc(stats.speed, stats.maxSpeed)
                    //Spacer(Modifier.height(8.dp))
                    GForceBar(value = stats.currentGForce, maxValue = stats.maxGForce)
                }
            }

            // Stats grid
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("DISTANCE", "0.0 km", Modifier.weight(1f))
                StatCard("TOTAL TIME", "00:00", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("MOVING",
                    formatSecondsToTime(stats.movingTime), Modifier.weight(1f), valueColor = NeonGreen)
                StatCard("STOPPED",
                    formatSecondsToTime(stats.stoppedTime), Modifier.weight(1f), valueColor = NeonRed)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("AVG SPEED", "${stats.avgSpeed.toInt()} km/h", Modifier.weight(1f))
                StatCard("MAX SPEED", "${stats.maxSpeed.toInt()} km/h", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("ELEVATION", "${stats.totalElevationGain.toInt()} m", Modifier.weight(1f))
                StatCard("MAX G", String.format("%.2f G", stats.maxGForce), Modifier.weight(1f), valueColor = NeonBlue)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextMuted, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SpeedometerArc(speedKmh: Float, maxSpeedKmh: Float = 200f) {
    val speedPercent = (speedKmh  / maxSpeedKmh ).coerceIn(0f, 1f)
    val arcBg = Color(0xFF1A1A30)
    val dotColor = NeonGreen

    // Outer box: fixed square so the arc always has room
    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val padding = strokeWidth / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
            val topLeft = Offset(padding, padding)
            val style = Stroke(width = strokeWidth, cap = StrokeCap.Round)

            val startAngle = 150f   // starts bottom-left
            val totalSweep = 240f   // sweeps to bottom-right

            // Background track
            drawArc(
                color = arcBg,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style
            )

            // Coloured progress
            if (speedPercent > 0f) {
                drawArc(
                    brush= Brush.sweepGradient(
                        colorStops = arrayOf(
                            0f  to NeonGreen,
                            1f  to NeonBlue
                        ),
                        center = Offset(size.width / 2, size.height / 2)
                    ),
                    startAngle = startAngle,
                    sweepAngle = totalSweep * speedPercent,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = style
                )
            }

            // Indicator dot at the tip of the progress arc
            val angleRad  = Math.toRadians(
                (startAngle + totalSweep * speedPercent).toDouble()
            )
            val radius = arcSize.width / 2
            val cx = topLeft.x + radius + radius * cos(angleRad).toFloat()
            val cy = topLeft.y + arcSize.height / 2 + radius * sin(angleRad).toFloat()
            drawCircle(color = dotColor, radius = 7.dp.toPx(), center = Offset(cx, cy))
        }

        // Speed number centred inside the arc
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = speedKmh.toInt().toString(),
                color = TextPrimary,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 72.sp
            )
            Text(
                text = "KM/H",
                color = TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp
            )
        }
    }
}
@Composable
fun GForceBar(value: Float, maxValue: Float) {
    val fillFraction = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
    val barGradient  = Brush.horizontalGradient(listOf(NeonGreen, NeonBlue))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(String.format("%.2f G", value), color = TextPrimary,
            fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1A1A30))
        ) {
            if (fillFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(barGradient)
                )
            }
            // Centre tick mark
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF3A3A5A))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("MAX: ${String.format("%.2f", maxValue)} G",
            color = TextMuted, fontSize = 11.sp)
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