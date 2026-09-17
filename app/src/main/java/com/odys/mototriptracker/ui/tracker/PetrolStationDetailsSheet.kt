package com.odys.mototriptracker.ui.tracker

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.odys.mototriptracker.data.petrol.GooglePetrolDetails
import com.odys.mototriptracker.data.petrol.OpeningHoursEvaluator
import com.odys.mototriptracker.data.petrol.PetrolPreferences
import com.odys.mototriptracker.data.petrol.PetrolStationRecommendation
import com.odys.mototriptracker.ui.theme.AppPalette
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PetrolStationDetailsSheet(
    station: PetrolStationRecommendation,
    preferences: PetrolPreferences,
    preferredOctanes: Set<Int>,
    googleDetails: GooglePetrolDetails?,
    googleDetailsLoading: Boolean,
    palette: AppPalette,
    onDismiss: () -> Unit,
    onGo: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bgDeep
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            PlaceHeroImage(
                photo = googleDetails?.photoBitmap,
                mapPreview = googleDetails?.mapPreviewBitmap,
                loading = googleDetailsLoading,
                palette = palette
            )

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    googleDetails?.name ?: station.name,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        label = formatDistance(station.distanceMeters),
                        tint = palette.neonBlue
                    )
                    StatusChip(
                        label = when {
                            googleDetails?.isOpenNow == true -> "Open now"
                            googleDetails?.isOpenNow == false -> "Closed now"
                            else -> openLabel(station.openStatus)
                        },
                        tint = when {
                            googleDetails?.isOpenNow == true -> palette.neonGreen
                            googleDetails?.isOpenNow == false -> palette.neonRed
                            station.openStatus == OpeningHoursEvaluator.Status.OPEN -> palette.neonGreen
                            station.openStatus == OpeningHoursEvaluator.Status.CLOSED -> palette.neonRed
                            else -> palette.routeAmber
                        }
                    )
                    if (station.hoursFromGoogle || googleDetails != null) {
                        StatusChip(label = "Google", tint = palette.neonBlue)
                    }
                    if (preferences.isPreferredBrand(station.brand ?: station.name)) {
                        StatusChip(label = "Preferred", tint = palette.neonGreen)
                    }
                    if (station.isHighwayAccessible) {
                        StatusChip(label = "Highway", tint = palette.neonBlue)
                    }
                }

                DetailCard(palette = palette) {
                    DetailLine(
                        "Address",
                        googleDetails?.address ?: station.address ?: "Unknown",
                        palette
                    )
                    val phone = googleDetails?.phone ?: station.phone
                    if (!phone.isNullOrBlank()) DetailLine("Phone", phone, palette)
                    val rating = googleDetails?.rating ?: station.rating
                    if (rating != null) {
                        val count = googleDetails?.ratingCount ?: station.ratingCount
                        DetailLine(
                            "Rating",
                            if (count != null) String.format(Locale.US, "%.1f · %d reviews", rating, count)
                            else String.format(Locale.US, "%.1f", rating),
                            palette
                        )
                    }
                    DetailLine("Octane", station.displayOctanes(preferredOctanes), palette)
                    station.brand?.let { DetailLine("Brand", it, palette) }
                    if (station.isHighwayAccessible) {
                        DetailLine("Access", "Highway / service area", palette)
                    }
                }

                val hours = googleDetails?.weekdayHours?.takeIf { it.isNotEmpty() }
                    ?.joinToString("\n")
                    ?: station.displayHours()
                DetailCard(palette = palette) {
                    DetailLine(
                        if (googleDetails?.weekdayHours?.isNotEmpty() == true) "Hours (Google)" else "Hours",
                        hours,
                        palette
                    )
                    googleDetails?.websiteUri?.let { DetailLine("Website", it, palette) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderSubtle)
                    ) {
                        Text("Close", color = palette.textSecondary)
                    }
                    Button(
                        onClick = {
                            val mapsUri = googleDetails?.googleMapsUri
                            val intent = if (!mapsUri.isNullOrBlank()) {
                                Intent(Intent.ACTION_VIEW, mapsUri.toUri())
                            } else {
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "geo:${station.latitude},${station.longitude}?q=${station.latitude},${station.longitude}(${Uri.encode(station.name)})".toUri()
                                )
                            }
                            context.startActivity(intent)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.neonBlue)
                    ) {
                        Icon(
                            Icons.Filled.Map,
                            contentDescription = null,
                            tint = palette.bgDeep,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Maps", color = palette.bgDeep, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onGo,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.neonGreen)
                    ) {
                        Icon(
                            Icons.Filled.Navigation,
                            contentDescription = null,
                            tint = palette.bgDeep,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Go", color = palette.bgDeep, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
