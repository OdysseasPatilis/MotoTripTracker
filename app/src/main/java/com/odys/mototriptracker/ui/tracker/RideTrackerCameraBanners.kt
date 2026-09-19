package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.application.TrafficCameraAlert
import com.odys.mototriptracker.application.TrafficCameraKind
import com.odys.mototriptracker.ui.theme.AppPalette

@Composable
internal fun TrafficCameraBanner(
    alert: TrafficCameraAlert,
    palette: AppPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bgPanel.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (alert.camera.kind == TrafficCameraKind.Speed) {
                Icons.Filled.CameraAlt
            } else {
                Icons.Filled.Traffic
            },
            contentDescription = null,
            tint = palette.routeAmber,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = alert.bannerText,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun TrafficCameraStatusBanner(
    text: String,
    palette: AppPalette,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = palette.textPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bgPanel.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

