package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.odys.mototriptracker.ui.theme.AppPalette

@Composable
internal fun TrackerBottomBar(
    isTracking: Boolean,
    isPaused: Boolean,
    isLocationEnabled: Boolean,
    palette: AppPalette,
    onPauseRide: () -> Unit,
    onStopRide: () -> Unit,
    onStartRide: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.bgDeep)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp, top = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isTracking) {
                Button(
                    onClick = onPauseRide,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RideTouchTarget),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.bgPanel),
                    border = BorderStroke(1.dp, palette.pauseBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                        tint = palette.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isPaused) "Resume" else "Pause",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = onStopRide,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RideTouchTarget),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.stopRed),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Stop", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onStartRide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RideTouchTarget),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLocationEnabled) palette.neonGreen else palette.startButtonDisabledBg
                    ),
                    enabled = isLocationEnabled
                ) {
                    Text(
                        if (isLocationEnabled) "Start Ride" else "Enable GPS to Start",
                        color = if (isLocationEnabled) palette.bgDeep else palette.startButtonDisabledText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

