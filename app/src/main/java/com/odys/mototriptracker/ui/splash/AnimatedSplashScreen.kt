package com.odys.mototriptracker.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated splash matching iOS `SplashView` — same assets, tagline, and motion.
 */
@Composable
fun AnimatedSplashScreen(
    onFinished: () -> Unit
) {
    val mint = Color(0xFF00E5A0)
    val blue = Color(0xFF00B4FF)
    val deep = Color(0xFF101014)

    val logoScale = remember { Animatable(0.72f) }
    val logoOpacity = remember { Animatable(0f) }
    val titleOpacity = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(12f) }
    val needleProgress = remember { Animatable(0f) }
    val dismissOpacity = remember { Animatable(1f) }
    var gpsBars by remember { mutableIntStateOf(0) }

    val infinite = rememberInfiniteTransition(label = "splashPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val roadPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "road"
    )

    LaunchedEffect(Unit) {
        launch {
            logoScale.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 180f))
        }
        launch {
            logoOpacity.animateTo(1f, tween(500))
        }
        launch {
            needleProgress.animateTo(1f, tween(1150))
        }
        launch {
            delay(250)
            titleOpacity.animateTo(1f, tween(550))
            titleOffset.animateTo(0f, tween(550))
        }
        launch {
            for (step in 1..4) {
                delay(if (step == 1) 200L else 180L)
                gpsBars = step
            }
        }

        delay(2150)
        launch { dismissOpacity.animateTo(0f, tween(450)) }
        launch { logoScale.animateTo(1.08f, tween(450)) }
        delay(450)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(deep)
            .alpha(dismissOpacity.value)
    ) {
        Image(
            painter = painterResource(R.drawable.splash_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.9f)
        )

        // Soft glow behind the mark
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 72.dp)
                .size(360.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            mint.copy(alpha = if (pulse > 0.5f) 0.28f else 0.12f),
                            blue.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                SplashSpeedometer(
                    progress = needleProgress.value,
                    mint = mint,
                    blue = blue,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(logoOpacity.value)
                )
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(108.dp)
                        .scale(logoScale.value)
                        .alpha(logoOpacity.value)
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Spacer(Modifier.height(28.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(titleOpacity.value)
                    .padding(top = titleOffset.value.dp)
            ) {
                Text(
                    text = "MotoTripTracker",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Track every ride",
                    color = mint.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(16.dp))
                SplashGPSBars(filled = gpsBars, tint = mint)
            }

            Spacer(Modifier.weight(1f))

            SplashRoad(
                phase = roadPhase,
                mint = mint,
                blue = blue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 48.dp)
                    .alpha(titleOpacity.value)
            )
        }
    }
}

@Composable
private fun SplashSpeedometer(
    progress: Float,
    mint: Color,
    blue: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f + 8f)
        val radius = min(size.width, size.height) * 0.42f
        val startDeg = 150f
        val totalSweep = 240f
        val stroke = 10.dp.toPx()

        drawArc(
            color = Color.White.copy(alpha = 0.08f),
            startAngle = startDeg,
            sweepAngle = totalSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        drawArc(
            brush = Brush.linearGradient(listOf(mint, blue)),
            startAngle = startDeg,
            sweepAngle = totalSweep * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val needleAngleRad = Math.toRadians((startDeg + totalSweep * progress).toDouble())
        val needleLength = radius - 18f
        val tip = Offset(
            center.x + (cos(needleAngleRad) * needleLength).toFloat(),
            center.y + (sin(needleAngleRad) * needleLength).toFloat()
        )
        drawLine(
            color = mint,
            start = center,
            end = tip,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = mint, radius = 5.dp.toPx(), center = center)
    }
}

@Composable
private fun SplashGPSBars(filled: Int, tint: Color) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(26.dp)
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((8 + index * 5).dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (index < filled) tint else tint.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun SplashRoad(
    phase: Float,
    mint: Color,
    blue: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val midY = size.height * 0.55f
        val steps = 40
        var prev = Offset(0f, midY)
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val x = t * size.width
            val y = midY - 18f * 4f * t * (1f - t)
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = prev,
                end = Offset(x, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            prev = Offset(x, y)
        }

        val dashCount = 7
        for (i in 0 until dashCount) {
            val t = ((i + phase) % dashCount) / dashCount
            val x = t * size.width
            val y = midY - 18f * 4f * t * (1f - t)
            drawRoundRect(
                color = if (i % 2 == 0) mint.copy(alpha = 0.85f) else blue.copy(alpha = 0.75f),
                topLeft = Offset(x - 10f, y - 1.5f),
                size = Size(20f, 3f),
                cornerRadius = CornerRadius(1.5f, 1.5f)
            )
        }
    }
}
