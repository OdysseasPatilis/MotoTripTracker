package com.odys.mototriptracker.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.odys.mototriptracker.ui.theme.AppPalette
import kotlin.math.roundToInt

enum class SpeedLimitFlashPhase { RED, BLUE, WHITE }

/** Shared red / blue / white flash cycle used by the sign and the full-screen overlay. */
@Composable
fun rememberSpeedLimitFlashPhase(isOverLimit: Boolean): SpeedLimitFlashPhase {
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
    return if (isOverLimit) {
        SpeedLimitFlashPhase.entries[(phaseProgress.roundToInt() % 3)]
    } else {
        SpeedLimitFlashPhase.WHITE
    }
}

fun speedLimitFlashFill(phase: SpeedLimitFlashPhase): Color = when (phase) {
    SpeedLimitFlashPhase.RED -> Color(0xFFE30613)
    SpeedLimitFlashPhase.BLUE -> Color(0xFF0055FF)
    SpeedLimitFlashPhase.WHITE -> Color.White
}

fun speedLimitFlashRing(phase: SpeedLimitFlashPhase): Color = when (phase) {
    SpeedLimitFlashPhase.RED, SpeedLimitFlashPhase.BLUE -> Color.White
    SpeedLimitFlashPhase.WHITE -> Color(0xFFE30613)
}

fun speedLimitFlashNumber(phase: SpeedLimitFlashPhase): Color = when (phase) {
    SpeedLimitFlashPhase.RED, SpeedLimitFlashPhase.BLUE -> Color.White
    SpeedLimitFlashPhase.WHITE -> Color.Black
}

private const val SCREEN_FLASH_ALPHA = 0.28f

/**
 * Full-screen translucent flash matching the speed-limit sign.
 * Uses [Canvas] (no pointer handlers) so Pause / Stop stay tappable underneath.
 */
@Composable
fun OverLimitScreenFlash(
    isOverLimit: Boolean,
    flashPhase: SpeedLimitFlashPhase,
    modifier: Modifier = Modifier
) {
    if (!isOverLimit) return

    val color = speedLimitFlashFill(flashPhase).copy(alpha = SCREEN_FLASH_ALPHA)
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedLimitSign(
    limitKmh: Int,
    isOverLimit: Boolean,
    palette: AppPalette,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    flashPhase: SpeedLimitFlashPhase = rememberSpeedLimitFlashPhase(isOverLimit),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val fill = if (isOverLimit) speedLimitFlashFill(flashPhase) else Color.White
    val ring = if (isOverLimit) speedLimitFlashRing(flashPhase) else palette.speedLimitRing
    val number = if (isOverLimit) speedLimitFlashNumber(flashPhase) else Color.Black

    val interactionSource = remember { MutableInteractionSource() }
    val interactionModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { onClick?.invoke() },
            onLongClick = { onLongClick?.invoke() }
        )
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
            .then(interactionModifier)
            .semantics {
                contentDescription = buildString {
                    append("Speed limit $limitKmh kilometers per hour")
                    if (isLive) append(", live road data")
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
