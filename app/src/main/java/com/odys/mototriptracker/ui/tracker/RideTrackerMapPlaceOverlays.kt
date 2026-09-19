package com.odys.mototriptracker.ui.tracker

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.odys.mototriptracker.application.PickedMapPlace
import com.odys.mototriptracker.ui.theme.AppPalette

@Composable
internal fun MapPlaceGoCard(
    place: PickedMapPlace,
    palette: AppPalette,
    onDismiss: () -> Unit,
    onGo: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.bgPanel.copy(alpha = 0.94f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Place,
                contentDescription = null,
                tint = palette.neonBlue,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    place.name,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                place.category?.takeIf { it.isNotBlank() }?.let { category ->
                    Text(
                        category,
                        color = palette.neonBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.neonBlue.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss place",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (place.address.isNotBlank()) {
            MapPlaceDetailRow(
                icon = Icons.Filled.Business,
                text = place.address,
                palette = palette,
            )
        }
        place.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            MapPlaceDetailRow(
                icon = Icons.Filled.Phone,
                text = phone,
                palette = palette,
                onClick = {
                    val digits = phone.filter { it.isDigit() || it == '+' }
                    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$digits".toUri()))
                },
            )
        }
        place.websiteHost?.takeIf { it.isNotBlank() }?.let { host ->
            MapPlaceDetailRow(
                icon = Icons.Filled.Language,
                text = host,
                palette = palette,
                onClick = {
                    val url = place.websiteUrl ?: return@MapPlaceDetailRow
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                },
            )
        }

        if (place.isResolving) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = palette.neonBlue
                )
                Text(
                    "Loading place details…",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Button(
            onClick = onGo,
            enabled = !place.isResolving,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = palette.neonGreen),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Go", color = palette.bgDeep, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun MapPlaceDetailRow(
    icon: ImageVector,
    text: String,
    palette: AppPalette,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text,
            color = palette.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

