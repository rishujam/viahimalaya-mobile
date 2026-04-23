package com.via.himalaya.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.RawSensors
import com.via.himalaya.data.models.SensorData
import com.via.himalaya.data.repository.TrekRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Cross-platform ViewModel for Trek tracking using official KMP ViewModel
 * Uses StateFlow for cross-platform state management instead of mutableStateOf
 * Uses viewModelScope which is now available in KMP
 */
class TrekViewModel(
    private val trekRepository: TrekRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(TrekScreenUIState())
    val state: StateFlow<TrekScreenUIState> = _state.asStateFlow()
    
    fun onEvent(event: TrekScreenUIEvent) {
        when(event) {
            is TrekScreenUIEvent.LocationUpdate -> {
                updateLocationPoint(event.sensorData)
            }
            
            is TrekScreenUIEvent.StartTrek -> {
                startTrek(event.name)
            }
            
            is TrekScreenUIEvent.StopTrek -> {
                stopTrek()
            }
            
            is TrekScreenUIEvent.PauseTrek -> {
                pauseTrek()
            }
        }
    }
    
    private fun startTrek(name: String) = viewModelScope.launch {
        try {
            val trekId = trekRepository.startNewTrek(name, "12345")
            _state.value = _state.value.copy(
                trekId = trekId,
                trekState = TrekState.Recording
            )
        } catch (e: Exception) {
            // Handle error - could emit to a separate error flow
            println("Error starting trek: ${e.message}")
        }
    }
    
    private fun stopTrek() = viewModelScope.launch {
        try {
            // Save any remaining points in buffer
            val currentState = _state.value
            if (currentState.pointsBuffer.isNotEmpty() && currentState.trekId != null) {
                trekRepository.saveBatch(currentState.trekId, currentState.pointsBuffer)
            }
            
            _state.value = _state.value.copy(
                trekId = null,
                trekState = TrekState.Idle,
                pointsBuffer = emptyList()
            )
        } catch (e: Exception) {
            println("Error stopping trek: ${e.message}")
        }
    }
    
    private fun pauseTrek() = viewModelScope.launch {
        _state.value = _state.value.copy(
            trekState = TrekState.Paused
        )
    }
    
    private fun updateLocationPoint(sensorData: SensorData) = viewModelScope.launch {
        val currentState = _state.value
        currentState.trekId?.let { trekId: String ->
            try {
                val rawSensors = RawSensors(
                    accelerometerX = sensorData.accelerometer?.getOrNull(0)?.toDouble(),
                    accelerometerY = sensorData.accelerometer?.getOrNull(1)?.toDouble(),
                    accelerometerZ = sensorData.accelerometer?.getOrNull(2)?.toDouble(),
                    gyroscopeX = sensorData.gyroscope?.getOrNull(0)?.toDouble(),
                    gyroscopeY = sensorData.gyroscope?.getOrNull(1)?.toDouble(),
                    gyroscopeZ = sensorData.gyroscope?.getOrNull(2)?.toDouble(),
                    magnetometerX = sensorData.magnetometer?.getOrNull(0)?.toDouble(),
                    magnetometerY = sensorData.magnetometer?.getOrNull(1)?.toDouble(),
                    magnetometerZ = sensorData.magnetometer?.getOrNull(2)?.toDouble(),
                    pressure = sensorData.pressure?.toDouble()
                )
                
                val point = Point(
                    trekId = trekId,
                    lat = sensorData.location?.lat ?: 0.0,
                    lon = sensorData.location?.lon ?: 0.0,
                    timestamp = Clock.System.now(),
                    altGps = sensorData.location?.altitude,
                    altBaro = sensorData.altBaro?.toDouble(),
                    accuracyH = sensorData.location?.accH,
                    accuracyV = sensorData.location?.accV,
                    speed = sensorData.location?.speed,
                    bearing = sensorData.location?.bearing,
                    battery = sensorData.battery,
                    rawSensors = rawSensors
                )
                
                val updatedPoints = currentState.pointsBuffer.toMutableList()
                updatedPoints.add(point)
                
                _state.value = currentState.copy(
                    pointsBuffer = updatedPoints
                )

                println("TrekViewModel: Adding location point")
                // Use efficient batching
                if (updatedPoints.size >= 10) {
                    println("TrekViewModel: Saving location points: $updatedPoints")
                    trekRepository.saveBatch(trekId, updatedPoints)
                    _state.value = _state.value.copy(pointsBuffer = emptyList())
                }
            } catch (e: Exception) {
                println("Error updating location point: ${e.message}")
            }
        }
    }
}