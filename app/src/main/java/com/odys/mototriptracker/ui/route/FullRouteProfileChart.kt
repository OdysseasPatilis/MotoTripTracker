package com.odys.mototriptracker.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.ui.theme.AppPalette
import java.util.Locale

// ── Profile chart (elevation or speed) ───────────────────────────────────────
@Composable
internal fun ProfileChart(
    summary: TripEntity,
    ridePoints: List<RidePoint>,
    activeLayer: MapLayer,
    palette: com.odys.mototriptracker.ui.theme.AppPalette
) {
    val values = remember(ridePoints, activeLayer) {
        ridePoints.map { if (activeLayer == MapLayer.Elevation) it.elevationM else it.speedKmh }
    }
    val lineColor = if (activeLayer == MapLayer.Elevation) FullRouteColors.Blue else FullRouteColors.RouteTeal
    val fillColor = if (activeLayer == MapLayer.Elevation) Color(0x1F5B9EF7) else Color(0x1F1D9E75)
    val peakVal = values.maxOrNull() ?: 0f
    val peakLabel = if (activeLayer == MapLayer.Elevation) "+${peakVal.toInt()} m peak"
    else "${peakVal.toInt()} km/h peak"
    val peakColor = if (activeLayer == MapLayer.Elevation) FullRouteColors.Blue else FullRouteColors.RouteCoral

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = if (activeLayer == MapLayer.Elevation) "ELEVATION PROFILE" else "SPEED PROFILE",
            color = palette.textSecondary, fontSize = 10.sp, letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.bgPanel)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val pad = 12f
                    val minV = values.minOrNull() ?: 0f
                    val maxV = values.maxOrNull() ?: 1f
                    val range = (maxV - minV).coerceAtLeast(1f)
                    val step = if (values.size > 1) (w - pad * 2) / (values.size - 1) else 0f
                    fun yFor(v: Float) = pad + (1f - (v - minV) / range) * (h - pad * 2)

                    val line = Path().apply {
                        values.forEachIndexed { i, v ->
                            if (i == 0) moveTo(pad, yFor(v)) else lineTo(pad + i * step, yFor(v))
                        }
                    }
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(pad + (values.size - 1) * step, h - pad)
                        lineTo(pad, h - pad)
                        close()
                    }
                    onDrawBehind {
                        drawPath(fill, fillColor)
                        drawPath(line, lineColor, style = Stroke(2f, cap = StrokeCap.Round))
                    }
                }
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 km", color = palette.textSecondary, fontSize = 9.sp)
            Text(peakLabel, color = peakColor, fontSize = 9.sp)
            Text("${String.format(Locale.US, "%.1f ", summary.distanceMeters / 1000f)} km", color = palette.textSecondary, fontSize = 9.sp)
        }
    }
}

// ── Legend pills ──────────────────────────────────────────────────────────────
@Composable
internal fun LegendPills(
    activeLayer: MapLayer,
    palette: com.odys.mototriptracker.ui.theme.AppPalette
) {
    val pills = if (activeLayer == MapLayer.Speed) listOf(
        Triple(FullRouteColors.RouteAmber, "Slow",   "0–40 km/h"),
        Triple(FullRouteColors.RouteTeal,  "Cruise", "40–130 km/h"),
        Triple(FullRouteColors.RouteCoral, "Fast",   "130+ km/h"),
    ) else listOf(
        Triple(FullRouteColors.RouteBlue,  "Flat",  "0–10 m"),
        Triple(FullRouteColors.RouteAmber, "Climb", "10–50 m"),
        Triple(FullRouteColors.RouteCoral, "Steep", "50 m+"),
    )
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pills.forEach { (color, label, range) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.bgPanel)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color))
                // Add maxLines = 1 to prevent wrapping
                Text(label, color = palette.textMuted, fontSize = 11.sp, maxLines = 1)
                Text(range, color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

