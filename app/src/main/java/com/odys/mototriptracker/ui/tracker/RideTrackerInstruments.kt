package com.odys.mototriptracker.ui.tracker

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BlurMaskFilter
import android.os.BatteryManager
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.domain.GpsQuality
import com.odys.mototriptracker.domain.TripStats
import com.odys.mototriptracker.domain.TwistinessCalculator
import com.odys.mototriptracker.ui.components.formatSecondsToTime
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun GpsSignalIndicator(
    quality: GpsQuality,
    accuracyMeters: Float?,
    palette: AppPalette
) {
    val tint = when (quality) {
        GpsQuality.EXCELLENT, GpsQuality.GOOD -> palette.neonGreen
        GpsQuality.FAIR -> palette.routeAmber
        GpsQuality.POOR -> palette.neonRed
        GpsQuality.UNKNOWN -> palette.textMuted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GpsBarsIcon(filledBars = quality.barCount, tint = tint)
        Text(
            text = if (accuracyMeters != null && accuracyMeters > 0f) {
                "±${accuracyMeters.toInt()}m"
            } else {
                "GPS"
            },
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun GpsBarsIcon(filledBars: Int, tint: Color) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        modifier = Modifier.height(13.dp)
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((5 + index * 2.5).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (index < filledBars) tint else tint.copy(alpha = 0.25f))
            )
        }
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

/**
 * Needle gauge with a large digital speed and the speed-limit badge.
 * Tick marks are unlabeled — numeric labels (20, 40, 60…) stay off the dial.
 */
@Composable
fun SpeedometerArc(
    speedKmh: Float,
    maxSpeedKmh: Float = 180f,
    speedLimitKmh: Float = 50f,
    isAutoLimit: Boolean = false,
    flashPhase: SpeedLimitFlashPhase = rememberSpeedLimitFlashPhase(speedKmh > speedLimitKmh),
    palette: AppPalette = LocalAppPalette.current,
    dialSize: Dp = 260.dp,
    floating: Boolean = false,
    speedReadoutColor: Color? = null,
) {
    val scaleMax = maxSpeedKmh.coerceAtLeast(1f)
    val isOverLimit = speedKmh > speedLimitKmh
    val limitPercent = (speedLimitKmh / scaleMax).coerceIn(0f, 1f)
    val speedPercent = (speedKmh / scaleMax).coerceIn(0f, 1f)
    val startAngle = 135f
    val totalSweep = 270f
    val speedFontSize = if (dialSize < 200.dp) 40.sp else 52.sp
    val unitFontSize = if (dialSize < 200.dp) 12.sp else 14.sp

    val speedNumColor by animateColorAsState(
        targetValue = if (isOverLimit) palette.stopRed else (speedReadoutColor ?: palette.textPrimary),
        animationSpec = tween(300),
        label = "speedNum"
    )

    Box(modifier = Modifier.size(dialSize), contentAlignment = Alignment.Center) {
        if (floating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                palette.bgDeep.copy(alpha = 0.82f),
                                palette.bgDeep.copy(alpha = 0.45f),
                                Color.Transparent,
                            )
                        ),
                        CircleShape,
                    )
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = if (dialSize < 200.dp) 16.dp.toPx() else 20.dp.toPx()
            val radius = (minOf(size.width, size.height) - padding * 2) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val trackStyle = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val mainWidth = if (dialSize < 200.dp) 7.dp.toPx() else 9.dp.toPx()
            val mainStyle = Stroke(width = mainWidth, cap = StrokeCap.Round)

            drawArc(
                color = palette.arcTrack,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = trackStyle
            )

            val tickStep = 20f
            val tickCount = (scaleMax / tickStep).toInt().coerceAtLeast(1)
            for (index in 0..tickCount) {
                val fraction = (index * tickStep / scaleMax).coerceIn(0f, 1f)
                val tickAngle = Math.toRadians((startAngle + totalSweep * fraction).toDouble())
                val inner = radius - 7.dp.toPx()
                val outer = radius + 1.dp.toPx()
                drawLine(
                    color = palette.textMuted.copy(alpha = 0.9f),
                    start = Offset(
                        center.x + inner * cos(tickAngle).toFloat(),
                        center.y + inner * sin(tickAngle).toFloat()
                    ),
                    end = Offset(
                        center.x + outer * cos(tickAngle).toFloat(),
                        center.y + outer * sin(tickAngle).toFloat()
                    ),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            if (speedPercent > 0f) {
                val accent = if (isOverLimit) palette.stopRed else palette.neonGreen
                val progressSweep = totalSweep * speedPercent

                drawIntoCanvas { canvas ->
                    val glowPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = accent.copy(alpha = 0.55f).toArgb()
                        strokeWidth = 16.dp.toPx()
                        style = android.graphics.Paint.Style.STROKE
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
                    }
                    val rect = android.graphics.RectF(
                        center.x - radius,
                        center.y - radius,
                        center.x + radius,
                        center.y + radius
                    )
                    canvas.nativeCanvas.drawArc(rect, startAngle, progressSweep, false, glowPaint)
                }

                if (!isOverLimit) {
                    drawArc(
                        color = palette.neonGreen,
                        startAngle = startAngle,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = mainStyle
                    )
                } else {
                    drawArc(
                        color = palette.neonGreen.copy(alpha = 0.6f),
                        startAngle = startAngle,
                        sweepAngle = totalSweep * limitPercent,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = mainStyle
                    )
                    drawArc(
                        color = palette.stopRed,
                        startAngle = startAngle + totalSweep * limitPercent,
                        sweepAngle = totalSweep * (speedPercent - limitPercent).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = mainStyle
                    )
                }
            }

            val limitAngleRad = Math.toRadians((startAngle + totalSweep * limitPercent).toDouble())
            val notchInner = radius - 9.dp.toPx()
            val notchOuter = radius + 9.dp.toPx()
            drawLine(
                color = palette.textPrimary.copy(alpha = 0.9f),
                start = Offset(
                    center.x + notchInner * cos(limitAngleRad).toFloat(),
                    center.y + notchInner * sin(limitAngleRad).toFloat()
                ),
                end = Offset(
                    center.x + notchOuter * cos(limitAngleRad).toFloat(),
                    center.y + notchOuter * sin(limitAngleRad).toFloat()
                ),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            val needleColor = if (isOverLimit) palette.stopRed else palette.neonBlue
            val needleAngle = Math.toRadians((startAngle + totalSweep * speedPercent).toDouble())
            val needleLength = radius - 6.dp.toPx()
            val tip = Offset(
                center.x + needleLength * cos(needleAngle).toFloat(),
                center.y + needleLength * sin(needleAngle).toFloat()
            )
            drawIntoCanvas { canvas ->
                val glowPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = needleColor.copy(alpha = 0.65f).toArgb()
                    strokeWidth = 7.dp.toPx()
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawLine(center.x, center.y, tip.x, tip.y, glowPaint)
            }
            drawLine(
                color = needleColor,
                start = center,
                end = tip,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = palette.bgCard, radius = 6.dp.toPx(), center = center)
            drawCircle(color = needleColor, radius = 3.5.dp.toPx(), center = center)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            val animatedSpeed by animateIntAsState(
                targetValue = speedKmh.toInt(),
                animationSpec = tween(1000, easing = FastOutSlowInEasing),
                label = "SpeedAnimation"
            )
            Text(
                text = animatedSpeed.toString(),
                color = speedNumColor,
                fontSize = speedFontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = speedFontSize
            )
            Text(
                "km/h",
                color = palette.textSecondary,
                fontSize = unitFontSize,
                fontWeight = FontWeight.Medium
            )
        }

        SpeedLimitSign(
            limitKmh = speedLimitKmh.toInt(),
            isOverLimit = isOverLimit,
            isLive = isAutoLimit,
            flashPhase = flashPhase,
            palette = palette,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = 6.dp)
        )
    }
}

@Composable
fun RideStatsGrid(
    stats: TripStats,
    palette: AppPalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                "DISTANCE",
                String.format(Locale.US, "%.1f km", stats.distanceKm),
                Modifier.weight(1f),
                palette = palette
            )
            StatCard(
                "TOTAL TIME",
                formatSecondsToTime(stats.tripTime),
                Modifier.weight(1f),
                palette = palette
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                "MOVING",
                formatSecondsToTime(stats.movingTime),
                Modifier.weight(1f),
                valueColor = palette.neonGreen,
                palette = palette
            )
            StatCard(
                "STOPPED",
                formatSecondsToTime(stats.stoppedTime),
                Modifier.weight(1f),
                valueColor = palette.neonRed,
                palette = palette
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                "AVG SPEED",
                "${stats.avgSpeed.toInt()} km/h",
                Modifier.weight(1f),
                palette = palette
            )
            StatCard(
                "MAX SPEED",
                "${stats.maxSpeed.toInt()} km/h",
                Modifier.weight(1f),
                palette = palette
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                "ELEVATION",
                "${stats.totalElevationGain.toInt()} m",
                Modifier.weight(1f),
                palette = palette
            )
            StatCard(
                "MAX G",
                String.format(Locale.US, "%.2f G", stats.maxGForce),
                Modifier.weight(1f),
                valueColor = palette.neonBlue,
                palette = palette
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                "TWISTINESS",
                TwistinessCalculator.formattedScore(
                    TwistinessCalculator.score(
                        stats.cornerCount,
                        stats.distanceKm.toDouble(),
                        stats.maxLateralGForce.toDouble()
                    )
                ),
                Modifier.weight(1f),
                valueColor = palette.neonBlue,
                palette = palette
            )
            StatCard(
                "CORNERS",
                "${stats.cornerCount}",
                Modifier.weight(1f),
                palette = palette
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
        Text(String.format(Locale.US, "%.2f G", value), color = palette.textPrimary,
            fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(5.dp)
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
        Spacer(Modifier.height(2.dp))
        Text("MAX: ${String.format(Locale.US, "%.2f", maxValue)} G",
            color = palette.textMuted, fontSize = 10.sp)
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
fun rememberBatteryLevel(): Int {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(100) }
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
