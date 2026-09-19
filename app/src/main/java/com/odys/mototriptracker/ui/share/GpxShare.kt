package com.odys.mototriptracker.ui.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.odys.mototriptracker.domain.model.Trip
import com.odys.mototriptracker.domain.model.displayTitle
import com.odys.mototriptracker.util.AppLogger
import java.io.File

object GpxShare {

    /** Shares a pre-built GPX document via the system share sheet. */
    fun share(context: Context, trip: Trip, gpxXml: String) {
        try {
            val cacheDir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(cacheDir, "ride_${trip.id}.gpx")
            file.writeText(gpxXml)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, trip.displayTitle())
                putExtra(Intent.EXTRA_TEXT, "GPX export — ${trip.displayTitle()}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Export GPX"))
            AppLogger.i(AppLogger.Category.UI, "GPX export launched for trip id=${trip.id}")
        } catch (t: Throwable) {
            AppLogger.e(AppLogger.Category.UI, "GPX export failed", t)
        }
    }
}
