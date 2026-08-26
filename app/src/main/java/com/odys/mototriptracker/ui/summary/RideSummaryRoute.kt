package com.odys.mototriptracker.ui.summary

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odys.mototriptracker.data.export.displayTitle
import com.odys.mototriptracker.ui.dashboard.RideSummaryScreenUpdate
import com.odys.mototriptracker.ui.share.GpxShare
import com.odys.mototriptracker.ui.share.RideShareCard
import com.odys.mototriptracker.ui.theme.LocalAppPalette

@Composable
fun RideSummaryRoute(
    onBack: () -> Unit,
    onViewRoute: (Long) -> Unit,
    viewModel: RideSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showShareOptions by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isDeleted, uiState.notFound) {
        if (uiState.isDeleted || uiState.notFound) {
            onBack()
        }
    }

    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.bgDeep),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = palette.neonGreen)
            }
        }

        uiState.trip != null -> {
            val trip = uiState.trip!!
            RideSummaryScreenUpdate(
                summary = trip,
                moments = uiState.moments,
                onBack = onBack,
                onDelete = { showDeleteConfirm = true },
                onShare = { showShareOptions = true },
                onRename = {
                    renameText = trip.title.orEmpty()
                    showRename = true
                },
                onToggleFavorite = viewModel::toggleFavorite,
                onViewRoute = { onViewRoute(trip.id) }
            )

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete this ride?") },
                    text = { Text("This can’t be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                viewModel.deleteTrip()
                            }
                        ) {
                            Text("Delete", color = palette.stopRed)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showRename) {
                AlertDialog(
                    onDismissRequest = { showRename = false },
                    title = { Text("Rename ride") },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            placeholder = { Text(trip.displayTitle()) }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRename = false
                                viewModel.renameTrip(renameText)
                            }
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRename = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showShareOptions) {
                AlertDialog(
                    onDismissRequest = { showShareOptions = false },
                    title = { Text("Share ride") },
                    text = { Text("Share a photo card or export the GPS track as GPX.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showShareOptions = false
                                RideShareCard.share(context, trip, uiState.moments)
                            }
                        ) {
                            Text("Share card")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showShareOptions = false
                                GpxShare.share(context, trip, uiState.routePoints)
                            }
                        ) {
                            Text("Export GPX")
                        }
                    }
                )
            }
        }

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.bgDeep),
                contentAlignment = Alignment.Center
            ) {
                Text("Trip not found", color = palette.textMuted)
            }
        }
    }
}
