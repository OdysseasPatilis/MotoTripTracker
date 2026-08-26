package com.odys.mototriptracker.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.domain.RideMoments
import com.odys.mototriptracker.ui.dashboard.formatSecondsToTime
import com.odys.mototriptracker.ui.dashboard.formatTimestampToDate
import com.odys.mototriptracker.util.AppLogger
import java.io.File
import java.io.FileOutputStream

object RideShareCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    fun share(context: Context, trip: TripEntity, moments: RideMoments) {
        try {
            val bitmap = render(trip, moments)
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
                putExtra(
                    Intent.EXTRA_TEXT,
                    buildShareText(trip)
                )
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
        return "MotoTripTracker ride — ${String.format("%.1f", km)} km · " +
            "max ${trip.maxSpeed.toInt()} km/h · ${formatTimestampToDate(trip.startTime)}"
    }

    fun render(trip: TripEntity, moments: RideMoments): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A0A0F.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bgPaint)

        // Hero gradient band
        val heroRect = RectF(48f, 64f, WIDTH - 48f, 280f)
        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                heroRect.left, heroRect.top, heroRect.right, heroRect.bottom,
                intArrayOf(0xFF5B5FEF.toInt(), 0xFF7C4DFF.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(heroRect, 48f, 48f, heroPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = 0.12f
        }
        canvas.drawText("MOTOTRIPTRACKER", 88f, 130f, titlePaint)

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5EFFC8.toInt()
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(formatTimestampToDate(trip.startTime), 88f, 210f, datePaint)

        val distanceKm = trip.distanceMeters / 1000f
        val totalTime = trip.movingTime + trip.stoppedTime
        val stats = listOf(
            Triple("DISTANCE", String.format("%.1f", distanceKm), "km"),
            Triple("MAX SPEED", trip.maxSpeed.toInt().toString(), "km/h"),
            Triple("AVG SPEED", trip.avgSpeed.toInt().toString(), "km/h"),
            Triple("MOVING", formatSecondsToTime(trip.movingTime), ""),
            Triple("ELEVATION", "+${trip.elevationGain.toInt()}", "m"),
            Triple("MAX G", String.format("%.2f", trip.maxGForce), "G")
        )

        var y = 340f
        stats.chunked(2).forEach { row ->
            var x = 48f
            row.forEach { (label, value, unit) ->
                drawStatTile(canvas, x, y, (WIDTH - 48f * 2 - 24f) / 2f, 170f, label, value, unit)
                x += (WIDTH - 48f * 2 - 24f) / 2f + 24f
            }
            y += 194f
        }

        if (moments.moments.isNotEmpty()) {
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF6B6B82.toInt()
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.1f
            }
            canvas.drawText("RIDE MOMENTS", 48f, y + 24f, sectionPaint)
            y += 56f

            moments.moments.take(3).forEach { moment ->
                drawMomentRow(canvas, 48f, y, WIDTH - 96f, moment.title, moment.value, moment.detail)
                y += 120f
            }
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4A4A6A.toInt()
            textSize = 28f
        }
        canvas.drawText(
            "Total ${formatSecondsToTime(totalTime)}  ·  Recorded with MotoTripTracker",
            48f,
            HEIGHT - 56f,
            footerPaint
        )

        return bitmap
    }

    private fun drawStatTile(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        label: String,
        value: String,
        unit: String
    ) {
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A2E.toInt()
        }
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 36f, 36f, tilePaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6B6B82.toInt()
            textSize = 26f
            letterSpacing = 0.08f
        }
        canvas.drawText(label, x + 28f, y + 48f, labelPaint)

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(value, x + 28f, y + 118f, valuePaint)

        if (unit.isNotBlank()) {
            val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF6B6B82.toInt()
                textSize = 26f
            }
            canvas.drawText(unit, x + 28f, y + 152f, unitPaint)
        }
    }

    private fun drawMomentRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        title: String,
        value: String,
        detail: String
    ) {
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF111120.toInt()
        }
        canvas.drawRoundRect(RectF(x, y, x + w, y + 104f), 28f, 28f, tilePaint)

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E5A0.toInt()
        }
        canvas.drawRoundRect(RectF(x, y + 24f, x + 8f, y + 80f), 8f, 8f, accent)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, x + 28f, y + 44f, titlePaint)

        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6B6B82.toInt()
            textSize = 26f
        }
        canvas.drawText(detail, x + 28f, y + 80f, detailPaint)

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E5A0.toInt()
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(value, x + w - 28f, y + 62f, valuePaint)
    }
}
