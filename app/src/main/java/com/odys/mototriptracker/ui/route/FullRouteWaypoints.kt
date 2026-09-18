package com.odys.mototriptracker.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.components.formatTimestampToDate

// ── Waypoints ─────────────────────────────────────────────────────────────────
@Composable
internal fun WaypointsPanel(
    summary: TripEntity,
    waypoints: List<Waypoint>,
    usedPolylineFallback: Boolean,
    palette: com.odys.mototriptracker.ui.theme.AppPalette,
    onWaypointClick: (LatLng) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Route waypoints", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(formatTimestampToDate(summary.startTime), color = palette.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (waypoints.isEmpty()) {
            Text(
                text = if (usedPolylineFallback) {
                    "Trail rebuilt from the summary polyline. Waypoints weren’t available for this ride."
                } else {
                    "No waypoints recorded for this ride."
                },
                color = palette.textSecondary,
                fontSize = 13.sp
            )
        } else {
            waypoints.forEachIndexed { i, wp ->
                WaypointRow(
                    wp,
                    showLine = i < waypoints.lastIndex,
                    palette = palette,
                    onClick = { onWaypointClick(wp.position) }
                )
            }
        }
    }
}

@Composable
internal fun WaypointRow(
    wp: Waypoint,
    showLine: Boolean,
    palette: com.odys.mototriptracker.ui.theme.AppPalette,
    onClick: () -> Unit
) {
    val style = waypointStyle(wp.type)
    val icon = style.icon
    val dotColor = style.color

    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top) {
        // --- TIMELINE GRAPHIC COLUMN ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Draw an Icon if we have one, otherwise fallback to the classic ring dot
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = wp.label,
                        tint = dotColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            } else {
                // The classic hollow dot for standard stops
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .drawWithCache {
                            onDrawBehind {
                                drawCircle(palette.bgDeep)
                                drawCircle(dotColor, style = Stroke(2f))
                                drawCircle(dotColor, radius = 3f)
                            }
                        }
                )
            }

            // Connecting Line
            if (showLine) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(38.dp) // Made slightly taller to fit the address subtext comfortably
                        .background(Color(0x1FFFFFFF))
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // --- TEXT COLUMN ---
        Column(modifier = Modifier.weight(1f)) {
            Text(wp.label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            // The detail text will now automatically display the actual Geocoded Street Name!
            Text(wp.detail, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1)
            if (showLine) Spacer(Modifier.height(20.dp))
        }

        // --- TIME COLUMN ---
        Text(wp.time, color = palette.textMuted, fontSize = 12.sp)
    }
}

