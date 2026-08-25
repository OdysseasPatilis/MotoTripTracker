package com.odys.mototriptracker.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.ui.theme.AppPalette
import kotlin.math.roundToInt

private enum class FlashPhase { RED, BLUE, WHITE }

@Composable
fun SpeedLimitSign(
    limitKmh: Int,
    isOverLimit: Boolean,
    palette: AppPalette,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val transition = rememberInfiniteTransition(label = "speedLimitFlash")
    val phaseProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flashPhase"
    )
    val phase = if (isOverLimit) {
        FlashPhase.entries[(phaseProgress.roundToInt() % 3)]
    } else {
        FlashPhase.WHITE
    }

    val fill = if (isOverLimit) flashFill(phase) else Color.White
    val ring = if (isOverLimit) flashRing(phase) else palette.speedLimitRing
    val number = if (isOverLimit) flashNumber(phase) else Color.Black

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(54.dp)
            .shadow(
                elevation = if (isOverLimit) 8.dp else 3.dp,
                shape = CircleShape,
                ambientColor = if (isOverLimit) fill.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.25f)
            )
            .background(fill, CircleShape)
            .border(width = 5.5.dp, color = ring, shape = CircleShape)
            .then(clickableModifier)
            .semantics {
                contentDescription = buildString {
                    append("Speed limit $limitKmh kilometers per hour")
                    if (isLive) append(", live road data")
                    if (onClick != null) append(". Tap to change manually.")
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = limitKmh.toString(),
            color = number,
            fontSize = if (limitKmh >= 100) 18.sp else 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun flashFill(phase: FlashPhase): Color = when (phase) {
    FlashPhase.RED -> Color(0xFFE30613)
    FlashPhase.BLUE -> Color(0xFF0055FF)
    FlashPhase.WHITE -> Color.White
}

private fun flashRing(phase: FlashPhase): Color = when (phase) {
    FlashPhase.RED, FlashPhase.BLUE -> Color.White
    FlashPhase.WHITE -> Color(0xFFE30613)
}

private fun flashNumber(phase: FlashPhase): Color = when (phase) {
    FlashPhase.RED, FlashPhase.BLUE -> Color.White
    FlashPhase.WHITE -> Color.Black
}
