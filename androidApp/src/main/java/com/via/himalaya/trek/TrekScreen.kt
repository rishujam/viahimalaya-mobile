package com.via.himalaya.trek

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.via.himalaya.SensorListener
import com.via.himalaya.presentation.TrekScreenUIEvent
import com.via.himalaya.presentation.TrekScreenUIState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TrekScreen(
    onEvent: (TrekScreenUIEvent) -> Unit,
    sensorListener: SensorListener,
    state: StateFlow<TrekScreenUIState>
) {
    // Collect the StateFlow as Compose State
    val uiState by state.collectAsState()
    
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
        
        Button(
            onClick = {
                sensorListener.startListening()
                onEvent(TrekScreenUIEvent.StartTrek("Test Trek"))
            }
        ) {
            Text("Start Trek")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                sensorListener.stopListening()
                onEvent(TrekScreenUIEvent.StopTrek)
            }
        ) {
            Text("Stop Trek")
        }
    }
}