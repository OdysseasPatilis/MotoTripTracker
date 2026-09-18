package com.odys.mototriptracker.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.odys.mototriptracker.domain.model.Trip
import com.odys.mototriptracker.domain.RideMoments
import com.odys.mototriptracker.ui.summary.CloudUploadStatus
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import java.util.Locale

@Composable
fun RideSummaryScreenUpdate(
    summary: Trip,
    moments: RideMoments = RideMoments(emptyList()),
    backendUrl: String = "",
    uploadStatus: CloudUploadStatus = CloudUploadStatus.Idle,
    onBack: () -> Unit = {},
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
    onRename: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onViewRoute: () -> Unit = {},
    onUpload: () -> Unit = {},
    onEditBackendUrl: () -> Unit = {},
) {
    val palette = LocalAppPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgDeep)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        TopBar(
            isFavorite = summary.isFavorite,
            onBack = onBack,
            onDelete = onDelete,
            onShare = onShare,
            onRename = onRename,
            onToggleFavorite = onToggleFavorite,
            palette = palette
        )
        Spacer(Modifier.height(4.dp))
        DateCard(summary)
        Spacer(Modifier.height(10.dp))
        MapPreviewCard(
            "${String.format(Locale.US, "%.1f ", summary.distanceMeters / 1000f)} km",
            summary.encodedRoutePolyline,
            onClick = onViewRoute
        )
        if (moments.moments.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            RideMomentsSection(moments = moments, palette = palette)
        }
        Spacer(Modifier.height(10.dp))
        StatsGrid(summary, palette)
        Spacer(Modifier.height(16.dp))
        CloudUploadSection(
            backendUrl = backendUrl,
            status = uploadStatus,
            onUpload = onUpload,
            onEditUrl = onEditBackendUrl,
            palette = palette,
        )
    }
}
