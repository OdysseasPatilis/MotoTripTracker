package com.odys.mototriptracker.ui.summary


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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.ui.summary.CloudUploadStatus
import com.odys.mototriptracker.ui.theme.AppPalette

@Composable
internal fun CloudUploadSection(
    backendUrl: String,
    status: CloudUploadStatus,
    onUpload: () -> Unit,
    onEditUrl: () -> Unit,
    palette: AppPalette,
) {
    val configured = backendUrl.isNotBlank()
    val isUploading = status is CloudUploadStatus.Uploading
    val statusText = when {
        !configured -> "Set your Mac server URL (e.g. http://192.168.1.7:8080), then upload."
        status is CloudUploadStatus.Idle -> "Send this ride to your Mac when you're on the same network."
        status is CloudUploadStatus.Uploading -> "Uploading…"
        status is CloudUploadStatus.Success -> "Uploaded successfully."
        status is CloudUploadStatus.Error -> status.message
        else -> ""
    }
    val statusColor = when (status) {
        CloudUploadStatus.Success -> palette.neonGreen
        is CloudUploadStatus.Error -> palette.stopRed
        else -> palette.textMuted
    }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "CLOUD SYNC",
            color = palette.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.bgCard)
                .clickable(enabled = !isUploading) {
                    if (configured) onUpload() else onEditUrl()
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.neonBlue.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = palette.neonBlue,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isUploading -> "Uploading…"
                            !configured -> "Set server URL"
                            else -> "Upload to server"
                        },
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                    )
                    if (configured) {
                        Text(
                            text = backendUrl,
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (configured) {
            Text(
                text = "Change server URL",
                color = palette.neonBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(onClick = onEditUrl)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

