package com.odys.mototriptracker.ui.dashboard

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.domain.TripStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import kotlinx.coroutines.delay
import java.util.Calendar

// --- Color palette ---
val BgDeep    = Color(0xFF0A0A0F)
val BgCard    = Color(0xFF111120)
val BgSurface = Color(0xFF4050E5)
val TextPrimary = Color.White
val TextMuted   = Color(0xFF4A4A6A)
val NeonGreen   = Color(0xFF00E5A0)
val NeonBlue    = Color(0xFF00B4FF)
val NeonRed     = Color(0xFFFF4A6A)

val GradientButton     = Brush.horizontalGradient(listOf(NeonGreen, NeonBlue))


@Composable
fun RideTrackerScreen(
    stats: TripStats,
    isTracking: Boolean,
    isLocationEnabled: Boolean,
    onStartRide: () -> Unit,
    onStopRide: () -> Unit,
    onViewHistory: () -> Unit,
    onPauseRide: () -> Unit,
    isPaused: Boolean
) {
    KeepScreenOn()
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isTracking) {
                        // Pause / Resume
                        Button(
                            onClick = onPauseRide,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E)),
                            border = BorderStroke(1.dp, Color(0x1FFFFFFF))
                        ) {
                            if (!isPaused) {
                                // Recording dot
                                Canvas(Modifier.size(8.dp)) {
                                    drawCircle(Color(0xFFE24B4A))
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                if (isPaused) "RESUME" else "PAUSE",
                                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        // Stop
                        Button(
                            onClick = onStopRide,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x26E24B4A)),
                            border = BorderStroke(1.dp, Color(0x4DE24B4A))
                        ) {
                            Text("STOP", color = Color(0xFFE24B4A),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    } else {
                        // Your existing single START RIDE button — keep unchanged
                        Button(
                            onClick = onStartRide,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                                .clip(RoundedCornerShape(28.dp)).background(GradientButton),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp),
                            enabled = isLocationEnabled
                        ) {
                            Text(
                                if (!isLocationEnabled) "ENABLE GPS TO START" else "START RIDE",
                                color = if (isLocationEnabled) BgDeep else Color.Gray,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                            )
                        }
                    }
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
            val currentTime = rememberCurrentTime()
            val batteryLevel = rememberBatteryLevel()

// Replace your existing top bar Row with:
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "RIDE TRACKER", color = TextMuted, fontSize = 10.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = 2.sp
                    )
                    Text(
                        currentTime, color = TextPrimary, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, lineHeight = 24.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BatteryIndicator(batteryLevel)
                    TextButton(onClick = onViewHistory) {
                        Text("HISTORY", color = NeonGreen, fontSize = 11.sp,
                            fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                    }
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
                StatCard("DISTANCE", "${String.format("%.1f km", stats.distanceKm)}", Modifier.weight(1f))
                StatCard("TOTAL TIME", formatSecondsToTime(stats.tripTime), Modifier.weight(1f))
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
fun SpeedometerArc(
    speedKmh: Float,
    maxSpeedKmh: Float = 260f,
    speedLimitKmh: Float = 90f
) {
    val isOverLimit = speedKmh > speedLimitKmh
    val limitPercent = (speedLimitKmh / maxSpeedKmh).coerceIn(0f, 1f)
    val speedPercent = (speedKmh / maxSpeedKmh).coerceIn(0f, 1f)

    val badgeBg by animateColorAsState(
        targetValue = if (isOverLimit) Color(0x26E24B4A) else Color(0x0FFFFFFF),
        animationSpec = tween(300),
        label = "badgeBg"
    )
    val badgeBorder by animateColorAsState(
        targetValue = if (isOverLimit) Color(0x66E24B4A) else Color(0x1FFFFFFF),
        animationSpec = tween(300),
        label = "badgeBorder"
    )
    val badgeNumColor by animateColorAsState(
        targetValue = if (isOverLimit) Color(0xFFE24B4A) else Color(0x55FFFFFF),
        animationSpec = tween(300),
        label = "badgeNum"
    )
    val speedNumColor by animateColorAsState(
        targetValue = if (isOverLimit) Color(0xFFE24B4A) else TextPrimary,
        animationSpec = tween(300),
        label = "speedNum"
    )
    Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {

        // Speed limit badge — top left of the arc box
        /*Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 0.dp, top = 0.dp)  // ← nudges it away from the arc tip
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg)
                    .border(
                        width = 0.5.dp,
                        color = badgeBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = speedLimitKmh.toInt().toString(),
                        color = badgeNumColor,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    Text("LIMIT", color = Color(0x40FFFFFF), fontSize = 8.sp, letterSpacing = 0.5.sp)
                }
            }
        }*/

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val padding = strokeWidth / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
            val topLeft = Offset(padding, padding)
            val style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            val startAngle = 150f
            val totalSweep = 240f

            // Background track
            drawArc(color = Color(0xFF252547), startAngle = startAngle,
                sweepAngle = totalSweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = style)

            if (speedPercent > 0f) {
                if (!isOverLimit) {
                    // Normal green→blue arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(0f to NeonGreen, 1f to NeonBlue),
                            center = Offset(size.width / 2, size.height / 2)
                        ),
                        startAngle = startAngle,
                        sweepAngle = totalSweep * speedPercent,
                        useCenter = false, topLeft = topLeft, size = arcSize, style = style
                    )
                } else {
                    // Teal arc up to the limit
                    drawArc(
                        color = NeonGreen.copy(alpha = 0.35f),
                        startAngle = startAngle,
                        sweepAngle = totalSweep * limitPercent,
                        useCenter = false, topLeft = topLeft, size = arcSize, style = style
                    )
                    // Red arc from limit to current speed
                    drawArc(
                        color = Color(0xFFE24B4A),
                        startAngle = startAngle + totalSweep * limitPercent,
                        sweepAngle = totalSweep * (speedPercent - limitPercent),
                        useCenter = false, topLeft = topLeft, size = arcSize, style = style
                    )
                }
            }

            // Indicator dot — keep your existing dot code here unchanged
            val angleRad = Math.toRadians((startAngle + totalSweep * speedPercent).toDouble())
            val radius = arcSize.width / 2
            val cx = topLeft.x + radius + radius * cos(angleRad).toFloat()
            val cy = topLeft.y + arcSize.height / 2 + radius * sin(angleRad).toFloat()
            drawCircle(
                color = if (isOverLimit) Color(0xFFE24B4A) else NeonGreen,
                radius = 7.dp.toPx(), center = Offset(cx, cy)
            )
        }

        // Your existing animated speed number — keep unchanged
        val animatedSpeed by animateIntAsState(
            targetValue = speedKmh.toInt(),
            animationSpec = tween(1000, easing = FastOutSlowInEasing),
            label = "SpeedAnimation"
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedSpeed.toString(),
                color = speedNumColor,              // ← animated
                fontSize = 72.sp, fontWeight = FontWeight.Bold, lineHeight = 72.sp
            )
            Text(
                "KM/H", color = TextMuted, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 3.sp
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
                .background(Color(0xFF252547))
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

@Composable
fun KeepScreenOn() {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window

        // 1. Add the flag when this Composable enters the screen
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            // 2. Safely clear the flag when the user navigates away
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun rememberCurrentTime(): String {
    var time by remember { mutableStateOf(currentTimeString()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            time = currentTimeString()
        }
    }
    return time
}

fun currentTimeString(): String {
    val cal = Calendar.getInstance()
    return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

@Composable
fun rememberBatteryLevel(): Int {
    val context = LocalContext.current
    var level by remember { mutableStateOf(100) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val raw  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (raw >= 0 && scale > 0) level = (raw * 100 / scale)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return level
}

@Composable
fun BatteryIndicator(level: Int) {
    val color = when {
        level <= 20 -> NeonRed
        level <= 50 -> Color(0xFFEF9F27)
        else        -> NeonGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(modifier = Modifier.size(width = 24.dp, height = 13.dp)) {
            val bodyW = size.width - 3.dp.toPx()
            val bodyH = size.height
            val termW = 3.dp.toPx()
            val termH = 5.dp.toPx()
            // Body outline
            drawRoundRect(
                color = Color(0x55FFFFFF),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Terminal nub
            drawRoundRect(
                color = Color(0x55FFFFFF),
                topLeft = Offset(bodyW, (bodyH - termH) / 2),
                size = Size(termW, termH),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
            // Fill
            val fillW = ((bodyW - 4.dp.toPx()) * level / 100f).coerceAtLeast(0f)
            drawRoundRect(
                color = color,
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(fillW, bodyH - 4.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
        Text("$level%", color = Color(0x80FFFFFF), fontSize = 11.sp)
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