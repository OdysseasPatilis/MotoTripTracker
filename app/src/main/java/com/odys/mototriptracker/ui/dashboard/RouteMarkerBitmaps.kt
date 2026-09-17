package com.odys.mototriptracker.ui.dashboard

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap

// ── Marker bitmaps (match waypoint list chips / hollow dots) ──────────────────
internal fun createIconBadgeBitmap(
    painter: Painter,
    tint: Color,
    density: Density,
    sizeDp: Float = 40f
): Bitmap {
    val px = with(density) { sizeDp.dp.roundToPx().coerceAtLeast(1) }
    val bmp = createBitmap(px, px)
    val androidCanvas = AndroidCanvas(bmp)
    val composeCanvas = ComposeCanvas(androidCanvas)
    val drawSize = Size(px.toFloat(), px.toFloat())

    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = composeCanvas,
        size = drawSize
    ) {
        val radius = size.minDimension / 2f
        drawCircle(color = tint.copy(alpha = 0.22f), radius = radius)
        drawCircle(
            color = tint.copy(alpha = 0.5f),
            radius = radius - 1.5f,
            style = Stroke(width = 2.5f)
        )
        val iconSize = size.minDimension * 0.55f
        val inset = (size.minDimension - iconSize) / 2f
        translate(inset, inset) {
            with(painter) {
                draw(
                    size = Size(iconSize, iconSize),
                    colorFilter = ColorFilter.tint(tint)
                )
            }
        }
    }
    return bmp
}

/** Hollow ring + core — matches the list timeline dots for stops. */
internal fun createHollowDotBitmap(colorArgb: Int, sizePx: Int = 54): Bitmap {
    val bmp = createBitmap(sizePx, sizePx)
    val canvas = AndroidCanvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val r = sizePx * 0.32f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(255, 14, 14, 20)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, r, fill)

    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.09f
    }
    canvas.drawCircle(cx, cy, r - ring.strokeWidth / 2f, ring)

    val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.1f, core)
    return bmp
}

/** Soft glow ring + solid core — matches iOS replay rider annotation. */
internal fun createRiderMarkerBitmap(colorArgb: Int): Bitmap {
    val px = 84
    val bmp = createBitmap(px, px)
    val canvas = AndroidCanvas(bmp)
    val cx = px / 2f
    val cy = px / 2f

    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(
            70,
            android.graphics.Color.red(colorArgb),
            android.graphics.Color.green(colorArgb),
            android.graphics.Color.blue(colorArgb)
        )
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, px * 0.42f, glow)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, px * 0.22f, fill)

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(255, 14, 14, 20)
        style = Paint.Style.STROKE
        strokeWidth = px * 0.06f
    }
    canvas.drawCircle(cx, cy, px * 0.22f, border)
    return bmp
}

