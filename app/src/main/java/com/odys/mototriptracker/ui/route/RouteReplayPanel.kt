package com.odys.mototriptracker.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.domain.RouteReplayEngine
import com.odys.mototriptracker.ui.theme.AppPalette
import kotlinx.coroutines.delay

@Composable
fun RouteReplayPanel(
    points: List<RoutePointEntity>,
    palette: AppPalette,
    onReplayPosition: (Double) -> Unit,
    onReplayPlayingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val engine = remember(points) { RouteReplayEngine(points) }
    if (!engine.isValid) return

    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackRate by remember { mutableDoubleStateOf(1.0) }
    val duration = engine.durationSeconds
    val frame = remember(elapsed) { engine.frame(elapsed) }

    LaunchedEffect(isPlaying) {
        onReplayPlayingChanged(isPlaying)
    }

    LaunchedEffect(isPlaying, playbackRate) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            delay(50)
            elapsed = (elapsed + 0.05 * playbackRate).coerceAtMost(duration)
            onReplayPosition(elapsed)
            if (elapsed >= duration) {
                isPlaying = false
                break
            }
        }
    }

    LaunchedEffect(elapsed) {
        onReplayPosition(elapsed)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bgCard)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        isPlaying = false
                    } else {
                        if (elapsed >= duration) elapsed = 0.0
                        isPlaying = true
                    }
                },
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = palette.neonGreen)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = {
                    isPlaying = false
                    elapsed = 0.0
                },
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = palette.textSecondary)
            ) {
                Icon(Icons.Filled.Replay, contentDescription = "Restart", modifier = Modifier.size(18.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1.0 to "1×", 2.0 to "2×", 5.0 to "5×").forEach { (rate, label) ->
                    SpeedChip(
                        label = label,
                        selected = playbackRate == rate,
                        palette = palette,
                        onClick = { playbackRate = rate }
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                frame?.let {
                    Text(
                        text = "${it.speedKmh.toInt()} km/h",
                        color = palette.neonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${formatReplayTime(elapsed)} / ${formatReplayTime(duration)}",
                    color = palette.textSecondary,
                    fontSize = 10.sp
                )
            }
        }

        Slider(
            value = if (duration > 0) (elapsed / duration).toFloat() else 0f,
            onValueChange = {
                isPlaying = false
                elapsed = duration * it
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = palette.neonGreen,
                activeTrackColor = palette.neonGreen,
                inactiveTrackColor = palette.bgPanel
            )
        )
    }
}

@Composable
private fun SpeedChip(
    label: String,
    selected: Boolean,
    palette: AppPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) palette.neonGreen.copy(alpha = 0.18f) else palette.bgPanel
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) palette.neonGreen else palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun formatReplayTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return String.format("%d:%02d", m, s)
}
