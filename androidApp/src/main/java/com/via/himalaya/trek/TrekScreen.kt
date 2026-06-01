package com.via.himalaya.trek

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.via.himalaya.presentation.navigator.NavigatorScreenUIEvent
import com.via.himalaya.presentation.navigator.NavigatorScreenUIState
import com.via.himalaya.service.TrekTrackingService
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TrekScreen(
    onEvent: (NavigatorScreenUIEvent) -> Unit,
    state: StateFlow<NavigatorScreenUIState>
) {
    val uiState by state.collectAsState()
    val context = LocalContext.current
    var localTrekName by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Trek ID: ${uiState.trekId ?: "No active trek"}",
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Trek State: ${uiState.trekState}",
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Points Buffer: ${uiState.pointsBuffer.size} points",
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        TextField(
            value = localTrekName,
            onValueChange = {
                localTrekName = it
                onEvent(NavigatorScreenUIEvent.NavigatorNameChanged(it))
            },
            label = { Text("Trek Name") },
            placeholder = { Text("Enter trek name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        
        Button(
            onClick = {
                onEvent(NavigatorScreenUIEvent.StartNavigator(localTrekName))
                TrekTrackingService.startTrekTracking(context, localTrekName)
                localTrekName = "" // Clear the text field
            },
            enabled = localTrekName.isNotBlank()
        ) {
            Text("Start Trek")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                TrekTrackingService.stopTrekTracking(context)
                onEvent(NavigatorScreenUIEvent.StopNavigator)
            }
        ) {
            Text("Stop Trek")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "All Points (${uiState.allPoints.size} total):",
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyColumn {
            items(uiState.allPoints) { point ->
                Text(
                    text = "Lat: ${point.lat}, Lon: ${point.lon}",
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}