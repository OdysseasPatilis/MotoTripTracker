package com.odys.mototriptracker.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.export.displayTitle
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.domain.RideMoment
import com.odys.mototriptracker.domain.RideMoments
import com.odys.mototriptracker.domain.TwistinessCalculator
import com.odys.mototriptracker.ui.dashboard.formatTimestampToDate
import com.odys.mototriptracker.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Share PNG card — map + moments layout matching iOS `RideShareCardRenderer`.
 */
object RideShareCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1620
    private const val MINT = 0xFF00E5A0.toInt()
    private const val BLUE = 0xFF00B4FF.toInt()
    private const val BG = 0xFF101014.toInt()

    fun share(
        context: Context,
        trip: TripEntity,
        moments: RideMoments,
        points: List<RoutePointEntity> = emptyList()
    ) {
        try {
            val bitmap = render(trip, moments, points)
            val cacheDir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(cacheDir, "ride_${trip.id}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, buildShareText(trip))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share ride"))
            AppLogger.i(AppLogger.Category.UI, "Share card launched for trip id=${trip.id}")
        } catch (t: Throwable) {
            AppLogger.e(AppLogger.Category.UI, "Share card failed", t)
        }
    }

    private fun buildShareText(trip: TripEntity): String {
        val km = trip.distanceMeters / 1000f
        return "MotoTripTracker ride — ${String.format("%.1f", km)} km · ${formatTimestampToDate(trip.startTime)}"
    }

    fun render(
        trip: TripEntity,
        moments: RideMoments,
        points: List<RoutePointEntity> = emptyList()
    ): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)

        canvas.drawColor(BG)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x73FFFFFF
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MOTOTRIPTRACKER", 48f, 80f, brandPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }
        canvas.drawText(truncate(trip.displayTitle(), titlePaint, WIDTH - 96f), 48f, 140f, titlePaint)

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MINT
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(formatTimestampToDate(trip.startTime), 48f, 190f, datePaint)

        val mapRect = RectF(48f, 220f, WIDTH - 48f, 780f)
        drawRouteMap(canvas, mapRect, trip, points)

        val statsRect = RectF(48f, mapRect.bottom + 24f, WIDTH - 48f, mapRect.bottom + 120f)
        drawStatsStrip(canvas, statsRect, trip)

        // Distance pill
        val pill = String.format("%.1f km", trip.distanceMeters / 1000f)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pillW = pillPaint.measureText(pill) + 36f
        val pillRect = RectF(
            mapRect.left + 24f,
            mapRect.bottom - 64f,
            mapRect.left + 24f + pillW,
            mapRect.bottom - 20f
        )
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x73000000 }
        canvas.drawRoundRect(pillRect, 22f, 22f, pillBg)
        canvas.drawText(pill, pillRect.left + 18f, pillRect.top + 30f, pillPaint)

        var y = statsRect.bottom + 36f
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x73FFFFFF
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MOMENTS", 48f, y, sectionPaint)
        y += 36f

        val shown = moments.moments.take(5)
        if (shown.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x59FFFFFF
                textSize = 26f
            }
            canvas.drawText("No moments for this ride", 48f, y + 28f, emptyPaint)
        } else {
            for (moment in shown) {
                y = drawMomentCard(canvas, moment, RectF(48f, y, WIDTH - 48f, y + 118f)) + 16f
            }
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x47FFFFFF
            textSize = 22f
        }
        canvas.drawText("Recorded with MotoTripTracker", 48f, HEIGHT - 48f, footerPaint)

        return bitmap
    }

    private fun drawRouteMap(
        canvas: Canvas,
        rect: RectF,
        trip: TripEntity,
        points: List<RoutePointEntity>
    ) {
        val coords = routeCoordinates(trip, points)
        val mapBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C22.toInt() }
        canvas.drawRoundRect(rect, 36f, 36f, mapBg)

        if (coords.size < 2) {
            val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x66FFFFFF
                textSize = 28f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("No route map", rect.centerX(), rect.centerY() + 10f, empty)
        } else {
            canvas.save()
            val clip = Path().apply { addRoundRect(rect, 36f, 36f, Path.Direction.CW) }
            canvas.clipPath(clip)

            // Subtle grid
            val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x14FFFFFF
                strokeWidth = 2f
            }
            var gx = rect.left + 40f
            while (gx < rect.right) {
                canvas.drawLine(gx, rect.top, gx, rect.bottom, grid)
                gx += 48f
            }
            var gy = rect.top + 40f
            while (gy < rect.bottom) {
                canvas.drawLine(rect.left, gy, rect.right, gy, grid)
                gy += 48f
            }

            val projected = project(coords, rect)
            val routePath = Path().apply {
                moveTo(projected[0].first, projected[0].second)
                for (i in 1 until projected.size) {
                    lineTo(projected[i].first, projected[i].second)
                }
            }

            val under = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = 0x59000000
            }
            canvas.drawPath(routePath, under)

            val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                shader = LinearGradient(
                    rect.left, rect.centerY(), rect.right, rect.centerY(),
                    intArrayOf(MINT, BLUE),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(routePath, routePaint)

            drawEndpoint(canvas, projected.first(), MINT)
            drawEndpoint(canvas, projected.last(), 0xFFE24B4A.toInt())
            canvas.restore()
        }

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x14FFFFFF
        }
        canvas.drawRoundRect(rect, 36f, 36f, border)
    }

    private fun drawEndpoint(canvas: Canvas, point: Pair<Float, Float>, color: Int) {
        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = BG }
        canvas.drawCircle(point.first, point.second, 9f, outer)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(point.first, point.second, 6f, fill)
    }

    private fun drawMomentCard(canvas: Canvas, moment: RideMoment, rect: RectF): Float {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0FFFFFFF }
        canvas.drawRoundRect(rect, 24f, 24f, bg)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8CFFFFFF.toInt()
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(truncate(moment.title, titlePaint, rect.width() * 0.55f), rect.left + 28f, rect.top + 38f, titlePaint)

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MINT
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(truncate(moment.value, valuePaint, rect.width() - 56f), rect.left + 28f, rect.top + 76f, valuePaint)

        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            textSize = 20f
        }
        canvas.drawText(truncate(moment.detail, detailPaint, rect.width() - 56f), rect.left + 28f, rect.top + 104f, detailPaint)
        return rect.bottom
    }

    private fun routeCoordinates(
        trip: TripEntity,
        points: List<RoutePointEntity>
    ): List<LatLng> {
        val encoded = trip.encodedRoutePolyline
        if (!encoded.isNullOrBlank()) {
            return try {
                PolyUtil.decode(encoded)
            } catch (_: Throwable) {
                emptyList()
            }
        }
        return points
            .sortedBy { it.timestamp }
            .map { LatLng(it.latitude, it.longitude) }
    }

    private fun project(coords: List<LatLng>, rect: RectF): List<Pair<Float, Float>> {
        var minLat = coords[0].latitude
        var maxLat = coords[0].latitude
        var minLng = coords[0].longitude
        var maxLng = coords[0].longitude
        for (c in coords) {
            minLat = minOf(minLat, c.latitude)
            maxLat = maxOf(maxLat, c.latitude)
            minLng = minOf(minLng, c.longitude)
            maxLng = maxOf(maxLng, c.longitude)
        }
        val latPad = maxOf((maxLat - minLat) * 0.12, 0.002)
        val lngPad = maxOf((maxLng - minLng) * 0.12, 0.002)
        minLat -= latPad
        maxLat += latPad
        minLng -= lngPad
        maxLng += lngPad

        val latSpan = (maxLat - minLat).coerceAtLeast(1e-6)
        val lngSpan = (maxLng - minLng).coerceAtLeast(1e-6)
        val inset = 28f
        val w = rect.width() - inset * 2
        val h = rect.height() - inset * 2

        return coords.map { c ->
            val x = rect.left + inset + (((c.longitude - minLng) / lngSpan) * w).toFloat()
            val y = rect.bottom - inset - (((c.latitude - minLat) / latSpan) * h).toFloat()
            x to y
        }
    }

    private fun drawStatsStrip(canvas: Canvas, rect: RectF, trip: TripEntity) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0FFFFFFF }
        canvas.drawRoundRect(rect, 20f, 20f, bg)

        val twistScore = TwistinessCalculator.score(trip)
        val items = listOf(
            "Max" to "${trip.maxSpeed.toInt()} km/h",
            "Twist" to if (twistScore > 0) TwistinessCalculator.formattedScore(twistScore) else "—",
            "Corners" to "${trip.cornerCount}",
            "Time" to formatMovingTime(trip.movingTime)
        )
        val columnWidth = rect.width() / items.size
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val twistPaint = Paint(valuePaint).apply { color = MINT }

        items.forEachIndexed { index, (label, value) ->
            val x = rect.left + columnWidth * index + 12f
            canvas.drawText(label.uppercase(Locale.US), x, rect.top + 28f, labelPaint)
            canvas.drawText(value, x, rect.top + 58f, if (index == 1) twistPaint else valuePaint)
        }
    }

    private fun formatMovingTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.take(end) + "…") > maxWidth) end--
        return text.take(end) + "…"
    }
}
