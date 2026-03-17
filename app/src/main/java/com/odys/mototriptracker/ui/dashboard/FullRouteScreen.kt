/*package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Colours ───────────────────────────────────────────────────────────────────
private val BgDark       = Color(0xFF0E0E14)
private val SurfaceDark  = Color(0xFF1A1A26)
private val CardDark     = Color(0xFF1C1C2A)
private val PurpleActive = Color(0xFF5B5FEF)
private val Mint         = Color(0xFF5EFFC8)
private val Blue         = Color(0xFF5B9EF7)
private val Yellow       = Color(0xFFFACC15)
private val RouteAmber   = Color(0xFFEF9F27)
private val RouteTeal    = Color(0xFF1D9E75)
private val RouteCoral   = Color(0xFFD85A30)
private val RouteBlue    = Color(0xFF378ADD)
private val TextHint     = Color(0x40FFFFFF)
private val Overlay      = Color(0xB20E0E14)

// ── Data ──────────────────────────────────────────────────────────────────────
*//*enum class MapLayer { Speed, Elevation }

data class Waypoint(
    val label: String,
    val detail: String,
    val time: String,
    val type: WaypointType
)

enum class WaypointType { Start, Stop, End }*//*

private val waypoints = listOf(
    Waypoint("Departure",    "Piraeus port area · 0.0 km",          "09:44", WaypointType.Start),
    Waypoint("Brief stop",   "Traffic light · 2.0 km · 01:02 pause","09:51", WaypointType.Stop),
    Waypoint("Brief stop",   "Intersection · 4.0 km · 00:42 pause", "09:53", WaypointType.Stop),
    Waypoint("Arrival",      "Destination · 6.0 km · +97m elev.",   "09:55", WaypointType.End),
)

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun FullRouteScreenGMaps(
    onBack: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    var activeLayer by remember { mutableStateOf(MapLayer.Speed) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        RouteTopBar(onBack = onBack, onShare = onShare)
        Spacer(Modifier.height(4.dp))
        RouteMapCard(activeLayer = activeLayer, onLayerChange = { activeLayer = it })
        Spacer(Modifier.height(12.dp))
        WaypointsPanel()
        Spacer(Modifier.height(12.dp))
        ElevationPanel(activeLayer = activeLayer)
        Spacer(Modifier.height(12.dp))
        SpeedLegendPills(activeLayer = activeLayer)
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────
@Composable
private fun RouteTopBar(onBack: () -> Unit, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CardDark)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }

        Text("Full route", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardDark)
                .clickable { onShare() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Share, "Share", tint = TextPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Map card ──────────────────────────────────────────────────────────────────
@Composable
private fun RouteMapCard(activeLayer: MapLayer, onLayerChange: (MapLayer) -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF131320))
    ) {
        FullRouteCanvas(
            activeLayer = activeLayer,
            modifier = Modifier.fillMaxSize()
        )

        // Layer toggle buttons
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LayerToggleButton(
                label = "Speed",
                isActive = activeLayer == MapLayer.Speed,
                onClick = { onLayerChange(MapLayer.Speed) }
            )
            LayerToggleButton(
                label = "Elevation",
                isActive = activeLayer == MapLayer.Elevation,
                onClick = { onLayerChange(MapLayer.Elevation) }
            )
        }

        // Legend bar bottom-left
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Overlay)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val (c1, c2, c3, legendText) = if (activeLayer == MapLayer.Speed) {
                listOf(RouteAmber, RouteTeal, RouteCoral, "Slow · Cruise · Fast")
            } else {
                listOf(RouteBlue, RouteAmber, RouteCoral, "Flat · Climb · Steep")
            }
            LegendSegment(c1 as Color); LegendSegment(c2 as Color); LegendSegment(c3 as Color)
            Spacer(Modifier.width(4.dp))
            Text(legendText as String, color = TextMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LayerToggleButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (isActive) PurpleActive else CardDark)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (isActive) TextPrimary else TextMuted,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

*//*@Composable
private fun LegendSegment(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 14.dp, height = 5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}*//*

// ── Route canvas ──────────────────────────────────────────────────────────────
@Composable
private fun FullRouteCanvas(activeLayer: MapLayer, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height

            fun pt(x: Float, y: Float) = Offset(x * w, y * h)

            val startPt = pt(0.11f, 0.83f)
            val stop1   = pt(0.29f, 0.71f)
            val stop2   = pt(0.45f, 0.49f)
            val midPt   = pt(0.60f, 0.29f)
            val endPt   = pt(0.95f, 0.17f)

            // Speed colours per segment
            val seg1Color = if (activeLayer == MapLayer.Speed) RouteAmber else RouteBlue
            val seg2Color = if (activeLayer == MapLayer.Speed) RouteTeal  else RouteAmber
            val seg3Color = if (activeLayer == MapLayer.Speed) RouteCoral else RouteCoral

            onDrawBehind {
                // Background
                drawRect(Color(0xFF131320))

                // Grid
                val gridColor = Color(0xFF1E1E30)
                for (f in listOf(0.25f, 0.5f, 0.75f)) {
                    drawLine(gridColor, Offset(0f, h * f), Offset(w, h * f), 1f)
                }
                for (f in listOf(0.16f, 0.32f, 0.48f, 0.64f, 0.80f)) {
                    drawLine(gridColor, Offset(w * f, 0f), Offset(w * f, h), 1f)
                }

                // Road highlights
                val roadColor = Color(0xFF252538)
                drawLine(roadColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 5f, cap = StrokeCap.Round)
                drawLine(roadColor, Offset(w * 0.32f, 0f), Offset(w * 0.32f, h), 5f, cap = StrokeCap.Round)
                drawLine(roadColor, Offset(w * 0.64f, 0f), Offset(w * 0.64f, h), 5f, cap = StrokeCap.Round)

                val thinStroke = Stroke(width = 5f, cap = StrokeCap.Round)
                val glowStroke = Stroke(width = 14f, cap = StrokeCap.Round)

                // Helper to draw a cubic bezier segment
                fun cubicPath(p0: Offset, p1: Offset, p2: Offset, p3: Offset) = Path().apply {
                    moveTo(p0.x, p0.y)
                    cubicTo(
                        p0.x + (p1.x - p0.x) * 0.5f, p0.y + (p1.y - p0.y) * 0.3f,
                        p1.x - (p1.x - p0.x) * 0.2f, p1.y,
                        p1.x, p1.y
                    )
                }

                val seg1Path = cubicPath(startPt, stop1, stop1, stop1)
                val seg2Path = cubicPath(stop1, midPt, midPt, midPt)
                val seg3Path = Path().apply {
                    moveTo(midPt.x, midPt.y)
                    cubicTo(
                        midPt.x + 40, midPt.y - 30,
                        endPt.x - 80, endPt.y + 20,
                        endPt.x, endPt.y
                    )
                }

                // Glow under full route
                val fullGlow = Path().apply {
                    moveTo(startPt.x, startPt.y)
                    cubicTo(startPt.x + 20, startPt.y - 20, stop1.x - 20, stop1.y + 10, stop1.x, stop1.y)
                    cubicTo(stop1.x + 20, stop1.y - 30, midPt.x - 40, midPt.y + 20, midPt.x, midPt.y)
                    cubicTo(midPt.x + 40, midPt.y - 30, endPt.x - 80, endPt.y + 20, endPt.x, endPt.y)
                }
                drawPath(fullGlow, Color(0x0AFFFFFF), style = glowStroke)

                // Coloured segments
                drawPath(seg1Path, seg1Color, style = thinStroke)
                drawPath(seg2Path, seg2Color, style = thinStroke)
                drawPath(seg3Path, seg3Color, style = thinStroke)

                // Elevation label badge on end point (elevation layer only)
                if (activeLayer == MapLayer.Elevation) {
                    drawRoundRect(
                        color = Color(0x40D85A30),
                        topLeft = Offset(endPt.x - 28f, endPt.y - 26f),
                        size = Size(56f, 18f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                    )
                }

                // Waypoint dots
                // Stop 1
                drawCircle(BgDark, radius = 9f, center = stop1)
                drawCircle(Yellow, radius = 9f, center = stop1, style = Stroke(2f))
                drawCircle(Yellow, radius = 3.5f, center = stop1)
                // Stop 2
                drawCircle(BgDark, radius = 9f, center = stop2)
                drawCircle(Yellow, radius = 9f, center = stop2, style = Stroke(2f))
                drawCircle(Yellow, radius = 3.5f, center = stop2)
                // Start
                drawCircle(BgDark, radius = 11f, center = startPt)
                drawCircle(Mint, radius = 11f, center = startPt, style = Stroke(2.5f))
                drawCircle(Mint, radius = 4.5f, center = startPt)
                // End
                drawCircle(Blue.copy(alpha = 0.18f), radius = 16f, center = endPt)
                drawCircle(BgDark, radius = 10f, center = endPt)
                drawCircle(Blue, radius = 10f, center = endPt, style = Stroke(2.5f))
                drawCircle(Blue, radius = 4f, center = endPt)

                // Compass circle (bottom-right)
                val cx = w - 26f; val cy = h - 26f
                drawCircle(CardDark, radius = 18f, center = Offset(cx, cy))
                drawCircle(Color(0x1FFFFFFF), radius = 18f, center = Offset(cx, cy), style = Stroke(0.5f))
            }
        }
    )
}

// ── Waypoints panel ───────────────────────────────────────────────────────────
@Composable
private fun WaypointsPanel() {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Route waypoints", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("Mar 17 · 09:44", color = TextHint, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))

        waypoints.forEachIndexed { index, wp ->
            WaypointRow(wp = wp, showLine = index < waypoints.lastIndex)
        }
    }
}

@Composable
private fun WaypointRow(wp: Waypoint, showLine: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Dot + connector line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            val dotColor = when (wp.type) {
                WaypointType.Start -> Mint
                WaypointType.Stop  -> Yellow
                WaypointType.End   -> Blue
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(BgDark)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .drawWithCache {
                            onDrawBehind {
                                drawCircle(BgDark)
                                drawCircle(dotColor, style = Stroke(2f))
                                drawCircle(dotColor, radius = 3f)
                            }
                        }
                )
            }
            if (showLine) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(32.dp)
                        .background(Color(0x1FFFFFFF))
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Info
        Column(modifier = Modifier.weight(1f).padding(bottom = if (showLine) 0.dp else 0.dp)) {
            Text(wp.label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(wp.detail, color = TextHint, fontSize = 11.sp)
            if (showLine) Spacer(Modifier.height(20.dp))
        }

        Text(wp.time, color = TextMuted, fontSize = 12.sp)
    }
}

// ── Elevation / speed profile chart ──────────────────────────────────────────
@Composable
private fun ElevationPanel(activeLayer: MapLayer) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = if (activeLayer == MapLayer.Elevation) "ELEVATION PROFILE" else "SPEED PROFILE",
            color = TextHint,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val pad = 12f

                    // Elevation points (normalised y, 0=top)
                    val elevPts = listOf(0.85f, 0.78f, 0.70f, 0.58f, 0.42f, 0.30f, 0.22f, 0.15f)
                    // Speed points
                    val speedPts = listOf(0.55f, 0.60f, 0.50f, 0.30f, 0.15f, 0.10f, 0.28f, 0.45f)

                    val pts = if (activeLayer == MapLayer.Elevation) elevPts else speedPts
                    val lineColor = if (activeLayer == MapLayer.Elevation) Blue else RouteTeal
                    val fillColor = if (activeLayer == MapLayer.Elevation)
                        Color(0x1F5B9EF7) else Color(0x1F1D9E75)

                    val xStep = (w - pad * 2) / (pts.size - 1)

                    val chartPath = Path().apply {
                        pts.forEachIndexed { i, y ->
                            val x = pad + i * xStep
                            val cy = pad + y * (h - pad * 2)
                            if (i == 0) moveTo(x, cy) else lineTo(x, cy)
                        }
                    }
                    val fillPath = Path().apply {
                        addPath(chartPath)
                        lineTo(pad + (pts.size - 1) * xStep, h - pad)
                        lineTo(pad, h - pad)
                        close()
                    }

                    onDrawBehind {
                        drawPath(fillPath, fillColor)
                        drawPath(chartPath, lineColor, style = Stroke(width = 2f, cap = StrokeCap.Round))

                        // Axis labels
                        // Distance labels drawn via text in compose below
                    }
                }
        )

        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 km", color = TextHint, fontSize = 9.sp)
            if (activeLayer == MapLayer.Elevation)
                Text("+97 m peak", color = Blue, fontSize = 9.sp)
            else
                Text("133 km/h peak", color = RouteCoral, fontSize = 9.sp)
            Text("6.0 km", color = TextHint, fontSize = 9.sp)
        }
    }
}

// ── Speed / elevation legend pills ───────────────────────────────────────────
@Composable
private fun SpeedLegendPills(activeLayer: MapLayer) {
    val pills = if (activeLayer == MapLayer.Speed) {
        listOf(
            Triple(RouteAmber, "Slow",   "0–40 km/h"),
            Triple(RouteTeal,  "Cruise", "40–80 km/h"),
            Triple(RouteCoral, "Fast",   "80+ km/h"),
        )
    } else {
        listOf(
            Triple(RouteBlue,  "Flat",   "0–10 m"),
            Triple(RouteAmber, "Climb",  "10–50 m"),
            Triple(RouteCoral, "Steep",  "50 m+"),
        )
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pills.forEach { (color, label, range) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(label, color = TextMuted, fontSize = 11.sp)
                Text(range, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}*/

// ── Preview ───────────────────────────────────────────────────────────────────
/*
@Preview(showBackground = true, backgroundColor = 0xFF0E0E14, widthDp = 390, heightDp = 900)
@Composable
fun FullRouteScreenPreview() {
    FullRouteScreenGMaps()
}*/
