package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.trip.TripEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripHistoryScreen(
    history: List<TripEntity>,
    onBack: () -> Unit,
    onDeleteTrip: (Long) -> Unit // NEW: Callback to handle deletion
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ride History", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.DarkGray)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No rides saved yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { trip ->
                    // Pass the delete action down to the card
                    TripHistoryCard(
                        trip = trip,
                        onDelete = { onDeleteTrip(trip.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun TripHistoryCard(
    trip: TripEntity,
    onDelete: () -> Unit // NEW: Delete callback
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Put the Title and the Delete Button in a Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestampToDate(trip.startTime),
                    color = Color.Cyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Trip",
                        tint = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Distance: String.format(\"%.1f km\", trip.distanceMeters / 1000f)", color = Color.White)
                Text("Max Speed: ${trip.maxSpeed.toInt()} km/h", color = Color.White)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Moving: ${formatSecondsToTime(trip.movingTime)}", color = Color.LightGray, fontSize = 14.sp)
                Text("Stopped: ${formatSecondsToTime(trip.stoppedTime)}", color = Color.LightGray, fontSize = 14.sp)
            }
            // ... (The rest of your existing Row data for Distance, Speed, Moving Time, etc.)
        }
    }
}