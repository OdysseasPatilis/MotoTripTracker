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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odys.mototriptracker.domain.GpsQuality
import com.odys.mototriptracker.domain.TripStats
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import com.odys.mototriptracker.ui.theme.LocalThemeStore
import com.odys.mototriptracker.ui.theme.ThemeMode
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import kotlinx.coroutines.delay
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import java.util.Calendar

@Composable
fun RideTrackerScreen(
    stats: TripStats,
    isTracking: Boolean,
    isLocationEnabled: Boolean,
    onStartRide: () -> Unit,
    onStopRide: () -> Unit,
    onViewHistory: () -> Unit,
    onViewLeaderboard: () -> Unit = {},
    onPauseRide: () -> Unit,
    isPaused: Boolean
) {
    val palette = LocalAppPalette.current
    val themeStore = LocalThemeStore.current
    val fallbackSpeedLimitKmh by themeStore.speedLimitKmh.collectAsStateWithLifecycle()
    val themeMode by themeStore.mode.collectAsStateWithLifecycle()
    val view = LocalView.current

    val effectiveSpeedLimitKmh = (stats.roadSpeedLimitKmh ?: fallbackSpeedLimitKmh).toFloat()
    val isLiveSpeedLimit = isTracking && stats.roadSpeedLimitKmh != null
    val isOverLimit = isTracking && !isPaused && stats.speed > effectiveSpeedLimitKmh
    val flashPhase = rememberSpeedLimitFlashPhase(isOverLimit)

    KeepScreenOn()
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = palette.bgDeep,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.bgDeep)
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
                            colors = ButtonDefaults.buttonColors(containerColor = palette.bgPanel),
                            border = BorderStroke(1.dp, palette.pauseBorder)
                        ) {
                            if (!isPaused) {
                                Canvas(Modifier.size(8.dp)) {
                                    drawCircle(palette.stopRed)
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                if (isPaused) "RESUME" else "PAUSE",
                                color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = onStopRide,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.stopRed.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, palette.stopRed.copy(alpha = 0.3f))
                        ) {
                            Text("STOP", color = palette.stopRed,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    } else {
                        Button(
                            onClick = onStartRide,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(if (isLocationEnabled) palette.startGradient else Brush.linearGradient(listOf(palette.startButtonDisabledBg, palette.startButtonDisabledBg))),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp),
                            enabled = isLocationEnabled
                        ) {
                            Text(
                                if (!isLocationEnabled) "ENABLE GPS TO START" else "START RIDE",
                                color = if (isLocationEnabled) palette.bgDeep else palette.startButtonDisabledText,
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
                        "RIDE TRACKER", color = palette.textMuted, fontSize = 10.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = 2.sp
                    )
                    Text(
                        currentTime, color = palette.textPrimary, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, lineHeight = 24.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    TrackingStatusRow(
                        isTracking = isTracking,
                        isPaused = isPaused,
                        gpsQuality = stats.gpsQuality,
                        gpsAccuracyMeters = stats.gpsAccuracyMeters,
                        palette = palette
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BatteryIndicator(batteryLevel, palette)
                    IconButton(
                        onClick = { themeStore.toggleTheme() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (themeMode == ThemeMode.DARK) {
                                Icons.Filled.LightMode
                            } else {
                                Icons.Filled.DarkMode
                            },
                            contentDescription = if (themeMode == ThemeMode.DARK) {
                                "Switch to light theme"
                            } else {
                                "Switch to dark theme"
                            },
                            tint = palette.textMuted
                        )
                    }
                    IconButton(
                        onClick = onViewLeaderboard,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = "Leaderboard",
                            tint = palette.neonGreen
                        )
                    }
                    TextButton(onClick = onViewHistory) {
                        Text("HISTORY", color = palette.neonGreen, fontSize = 11.sp,
                            fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(palette.bgPanel)
                    .padding(24.dp, 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SpeedometerArc(
                        speedKmh = stats.speed,
                        maxSpeedKmh = maxOf(stats.maxSpeed, 260f),
                        speedLimitKmh = effectiveSpeedLimitKmh,
                        isLiveSpeedLimit = isLiveSpeedLimit,
                        flashPhase = flashPhase,
                        palette = palette,
                        onCycleSpeedLimit = if (isLiveSpeedLimit) {
                            null
                        } else {
                            {
                                themeStore.cycleSpeedLimit()
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                        }
                    )
                    GForceBar(
                        value = stats.currentGForce,
                        maxValue = stats.maxGForce,
                        palette = palette
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("DISTANCE", "${String.format("%.1f km", stats.distanceKm)}", Modifier.weight(1f), palette = palette)
                StatCard("TOTAL TIME", formatSecondsToTime(stats.tripTime), Modifier.weight(1f), palette = palette)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("MOVING", formatSecondsToTime(stats.movingTime), Modifier.weight(1f), valueColor = palette.neonGreen, palette = palette)
                StatCard("STOPPED", formatSecondsToTime(stats.stoppedTime), Modifier.weight(1f), valueColor = palette.neonRed, palette = palette)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("AVG SPEED", "${stats.avgSpeed.toInt()} km/h", Modifier.weight(1f), palette = palette)
                StatCard("MAX SPEED", "${stats.maxSpeed.toInt()} km/h", Modifier.weight(1f), palette = palette)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("ELEVATION", "${stats.totalElevationGain.toInt()} m", Modifier.weight(1f), palette = palette)
                StatCard("MAX G", String.format("%.2f G", stats.maxGForce), Modifier.weight(1f), valueColor = palette.neonBlue, palette = palette)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "LATERAL G",
                    String.format("%.2f G", if (isTracking) stats.currentLateralGForce else stats.maxLateralGForce),
                    Modifier.weight(1f),
                    valueColor = palette.neonGreen,
                    palette = palette
                )
                StatCard("CORNERS", "${stats.cornerCount}", Modifier.weight(1f), palette = palette)
            }
            Spacer(Modifier.height(4.dp))
        }
    }

        // Full-screen translucent flash — same red/blue/white cycle as the sign.
        OverLimitScreenFlash(
            isOverLimit = isOverLimit,
            flashPhase = flashPhase,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun TrackingStatusRow(
    isTracking: Boolean,
    isPaused: Boolean,
    gpsQuality: GpsQuality,
    gpsAccuracyMeters: Float?,
    palette: AppPalette
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isTracking) {
            val pulse = rememberInfiniteTransition(label = "recPulse")
            val alpha by pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "recAlpha"
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(if (isPaused) 1f else alpha)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isPaused) palette.textMuted else palette.stopRed)
                )
                Text(
                    text = if (isPaused) "PAUSED" else "RECORDING",
                    color = if (isPaused) palette.textMuted else palette.stopRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        val gpsLabel = when {
            !isTracking -> if (gpsAccuracyMeters == null) "GPS READY" else "GPS"
            gpsQuality == GpsQuality.GOOD -> "GPS GOOD"
            gpsQuality == GpsQuality.FAIR -> "GPS FAIR"
            gpsQuality == GpsQuality.POOR -> "GPS WEAK"
            else -> "GPS…"
        }
        val gpsColor = when (gpsQuality) {
            GpsQuality.GOOD -> palette.neonGreen
            GpsQuality.FAIR -> palette.routeAmber
            GpsQuality.POOR -> palette.stopRed
            GpsQuality.UNKNOWN -> palette.textMuted
        }
        Text(
            text = buildString {
                append(gpsLabel)
                gpsAccuracyMeters?.let { append(" ±${it.toInt()}m") }
            },
            color = gpsColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    palette: AppPalette = LocalAppPalette.current
) {
    val resolvedValueColor = valueColor ?: palette.textPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.bgCard)
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(20.dp))
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = palette.textMuted, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = resolvedValueColor, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SpeedometerArc(
    speedKmh: Float,
    maxSpeedKmh: Float = 260f,
    speedLimitKmh: Float = 50f,
    isLiveSpeedLimit: Boolean = false,
    flashPhase: SpeedLimitFlashPhase = rememberSpeedLimitFlashPhase(speedKmh > speedLimitKmh),
    palette: AppPalette = LocalAppPalette.current,
    onCycleSpeedLimit: (() -> Unit)? = null
) {
    val isOverLimit = speedKmh > speedLimitKmh
    val limitPercent = (speedLimitKmh / maxSpeedKmh).coerceIn(0f, 1f)
    val speedPercent = (speedKmh / maxSpeedKmh).coerceIn(0f, 1f)

    val speedNumColor by animateColorAsState(
        targetValue = if (isOverLimit) palette.stopRed else palette.textPrimary,
        animationSpec = tween(300),
        label = "speedNum"
    )
    Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {

        SpeedLimitSign(
            limitKmh = speedLimitKmh.toInt(),
            isOverLimit = isOverLimit,
            isLive = isLiveSpeedLimit,
            flashPhase = flashPhase,
            palette = palette,
            onClick = onCycleSpeedLimit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 8.dp)
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val padding = strokeWidth / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
            val topLeft = Offset(padding, padding)
            val style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            val startAngle = 150f
            val totalSweep = 240f

            drawArc(color = palette.arcTrack, startAngle = startAngle,
                sweepAngle = totalSweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = style)

            if (speedPercent > 0f) {
                if (!isOverLimit) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(0f to palette.neonGreen, 1f to palette.neonBlue),
                            center = Offset(size.width / 2, size.height / 2)
                        ),
                        startAngle = startAngle,
                        sweepAngle = totalSweep * speedPercent,
                        useCenter = false, topLeft = topLeft, size = arcSize, style = style
                    )
                } else {
                    drawArc(
                        color = palette.neonGreen.copy(alpha = 0.35f),
                        startAngle = startAngle,
                        sweepAngle = totalSweep * limitPercent,
                        useCenter = false, topLeft = topLeft, size = arcSize, style = style
                    )
                    drawArc(
                        color = palette.stopRed,
                        startAngle = startAngle + totalSweep * limitPercent,
                        sweepAngle = totalSweep * (speedPercent - limitPercent),
                        useCenter = false, topLeft = topLeft, size = arcSize, style = style
                    )
                }
            }

            val angleRad = Math.toRadians((startAngle + totalSweep * speedPercent).toDouble())
            val radius = arcSize.width / 2
            val cx = topLeft.x + radius + radius * cos(angleRad).toFloat()
            val cy = topLeft.y + arcSize.height / 2 + radius * sin(angleRad).toFloat()
            drawCircle(
                color = if (isOverLimit) palette.stopRed else palette.neonGreen,
                radius = 7.dp.toPx(), center = Offset(cx, cy)
            )
        }

        val animatedSpeed by animateIntAsState(
            targetValue = speedKmh.toInt(),
            animationSpec = tween(1000, easing = FastOutSlowInEasing),
            label = "SpeedAnimation"
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedSpeed.toString(),
                color = speedNumColor,
                fontSize = 72.sp, fontWeight = FontWeight.Bold, lineHeight = 72.sp
            )
            Text(
                "KM/H", color = palette.textMuted, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 3.sp
            )
        }
    }
}
@Composable
fun GForceBar(
    value: Float,
    maxValue: Float,
    palette: AppPalette = LocalAppPalette.current
) {
    val fillFraction = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(String.format("%.2f G", value), color = palette.textPrimary,
            fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.arcTrack)
        ) {
            if (fillFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(palette.startGradient)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(palette.gForceTick)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("MAX: ${String.format("%.2f", maxValue)} G",
            color = palette.textMuted, fontSize = 11.sp)
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
fun BatteryIndicator(level: Int, palette: AppPalette = LocalAppPalette.current) {
    val color = when {
        level <= 20 -> palette.neonRed
        level <= 50 -> palette.routeAmber
        else        -> palette.neonGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(modifier = Modifier.size(width = 24.dp, height = 13.dp)) {
            val bodyW = size.width - 3.dp.toPx()
            val bodyH = size.height
            val termW = 3.dp.toPx()
            val termH = 5.dp.toPx()
            drawRoundRect(
                color = palette.batteryOutline,
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawRoundRect(
                color = palette.batteryOutline,
                topLeft = Offset(bodyW, (bodyH - termH) / 2),
                size = Size(termW, termH),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
            val fillW = ((bodyW - 4.dp.toPx()) * level / 100f).coerceAtLeast(0f)
            drawRoundRect(
                color = color,
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(fillW, bodyH - 4.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
        Text("$level%", color = palette.batteryLabel, fontSize = 11.sp)
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