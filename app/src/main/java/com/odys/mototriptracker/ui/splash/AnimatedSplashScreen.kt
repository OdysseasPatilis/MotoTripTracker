package com.odys.mototriptracker.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.R
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Branded launch screen with a live speedometer sweep, pulsing ring, and route dash.
 */
@Composable
fun AnimatedSplashScreen(
    onFinished: () -> Unit
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        started = true
        delay(2400)
        onFinished()
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(700),
        label = "splashAlpha"
    )
    val contentScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.86f,
        animationSpec = tween(900),
        label = "splashScale"
    )

    val infinite = rememberInfiniteTransition(label = "splashMotion")
    val needleAngle by infinite.animateFloat(
        initialValue = -110f,
        targetValue = 110f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "needle"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val routeProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "route"
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val bg = Color(0xFF0A0A14)
    val neonGreen = Color(0xFF00E5A0)
    val neonBlue = Color(0xFF3D7EFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(bg, Color(0xFF111120), bg)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Soft ambient orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(neonGreen.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.3f, size.height * 0.35f),
                    radius = size.minDimension * 0.45f
                ),
                radius = size.minDimension * 0.45f,
                center = Offset(size.width * 0.3f, size.height * 0.35f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(neonBlue.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.72f, size.height * 0.62f),
                    radius = size.minDimension * 0.4f
                ),
                radius = size.minDimension * 0.4f,
                center = Offset(size.width * 0.72f, size.height * 0.62f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(contentAlpha)
                .scale(contentScale)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(pulse)
                ) {
                    val stroke = 10.dp.toPx()
                    val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
                    val topLeft = Offset(stroke / 2f, stroke / 2f)

                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(neonBlue, neonGreen, neonBlue)),
                        startAngle = 140f,
                        sweepAngle = 260f * ((needleAngle + 110f) / 220f).coerceIn(0.15f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )

                    // Animated route dash around the badge
                    val radius = size.minDimension * 0.42f
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val points = 48
                    for (i in 0 until points) {
                        val t = i / points.toFloat()
                        if (t > routeProgress) break
                        val angle = Math.toRadians((t * 360.0) - 90.0)
                        val x = cx + radius * cos(angle).toFloat()
                        val y = cy + radius * sin(angle).toFloat()
                        drawCircle(
                            color = neonGreen.copy(alpha = 0.35f + 0.45f * t),
                            radius = 2.5f,
                            center = Offset(x, y)
                        )
                    }

                    rotate(needleAngle) {
                        drawLine(
                            color = neonGreen.copy(alpha = 0.95f),
                            start = Offset(cx, cy),
                            end = Offset(cx, cy - radius * 0.78f),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = neonGreen.copy(alpha = glowAlpha),
                            radius = 10.dp.toPx(),
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = Offset(cx, cy)
                        )
                    }
                }

                Image(
                    painter = painterResource(R.drawable.splash_brand),
                    contentDescription = null,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "MotoTripTracker",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ride. Track. Replay.",
                color = neonGreen.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp
            )
        }
    }
}
